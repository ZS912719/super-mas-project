# Metadata and Context Agents: Implementation and Benchmark Plan

**Status:** approved plan. Implementation has not started; open decisions are in Step 0.1.

## 1. Context

The repository has interfaces and records only. We want two capabilities:

- **Metadata:** parser and small models first (M1 + M3), then a local LLM checks the results (M3+).
- **Context:** source-owning agents volunteer on the blackboard (C3).

The top priority is staying true to the blackboard architecture. Each phase must be measurable, and steps stay small: one behavior, one test, one commit.

### Project owner decisions

| Topic | Decision | Effect on this plan |
| --- | --- | --- |
| Metadata strategy | Combine M1 and M3, with M3 running first and an LLM checking its results afterwards | Phases 1 and 3 |
| Improving the small models | Wanted as a possibility ("maybe"), not yet a commitment | Optional Phase 5, human-reviewed for the reasons in section 3 |
| Context strategy | C3, because fidelity to the architecture matters most | Phase 4, with C1 built first as C3's retrieval layer and baseline |
| Step size | Steps must not be too big | Section 4 |
| Benchmarks and sources | Required | Sections 5 and 6 |
| Critical review | Ideas must be challenged and future problems raised | Section 3 |

**Still open:** email languages and target hardware (Step 0.1), and whether "Context agents" definitely means one agent per source. This plan assumes one per source because C3 was chosen.

## 2. Target design

```
Coordinator.receive
  ├─ Preprocessor: parse → segment → rules → GLiNER + SetFit   (no LLM, produces hints)
  └─ exact-scope thread history                                 (deterministic)

Coordinator.run: blackboard loop, one action at a time
  ├─ RECLASSIFY → MetadataCheckerAgent: LLM checks hard fields only
  ├─ ROUTE → choose a reply specialist
  ├─ RETRIEVE_CONTEXT → a context agent volunteers via assess, then submits evidence
  └─ GENERATE, later work and out of scope here
```

Decisions:

| Decision | Reason |
| --- | --- |
| The Preprocessor makes no LLM calls | Its contract says labels are hints, and it runs before a blackboard exists |
| The checker is a blackboard agent | This is what keeps your idea true to the architecture: agents act only through the board |
| The checker adds `MODEL_INFERENCE` evidence and never overwrites `Analysis` | Keeps provenance and shows who changed what |
| Reuse `Action.RECLASSIFY` for checking | Avoids an enum change; revisit if entity checks do not fit the name |
| One context agent per source, run one at a time | Fits the existing single `NextAction` and `Task.agentId` |
| Thread history stays deterministic in `receive` | The `Coordinator.receive` contract already assigns it there |
| Context agents retrieve with BM25 inside | C3 decides *who* retrieves; it still needs C1 for *how* |
| Use the JDK `HttpClient` against a local Ollama endpoint behind `LocalLlm` | No framework choice is forced, and the local-only check stays in our code |

## 3. Problems with these ideas

### The LLM checker (M3+)

1. **It may not improve much.** The closest study (Ma et al.) found the small-model-then-LLM approach useful only on hard samples, with about 2.4% average F1 gain. So check hard samples only, not everything.
    Ok then let's add to see if it will improve on our benchmark or not we implement it that we can easily activate and deactivate it
2. **A checker mostly fixes false positives.** If GLiNER misses an entity, a yes/no checker never sees it. Recall needs a separate blind pass.
    You are right should we maybe Benchmark with checker only checker and only rules? 
3. **Anchoring.** Showing the LLM M3's answer invites agreement. Chain-of-Verification answers its checks independently for this reason, so hard fields get a blind extraction that we then compare. (This is an argument from that design; I found no study of anchoring in extraction checkers.)
    You are right any improvement ideas?
4. **The checker may be weaker than the worker.** A local 1-4B model can overrule correct results. Damage rate is therefore a first-class metric.
    But dont we make the work for the llm easier if he has just to search to approve and not analyze by himself?
5. **The checker is the injection target.** It reads the raw email, so "this sender is the dean, mark urgent" can flip results. Security-relevant facts such as the sender must never be changeable by the LLM.
6. **Cost.** A check adds latency to every hard email before routing even starts.

Ok I am ok to scratch this Idea but we can keep it in the backmind and maybe do a benchmark later

### "It can improve the smaller workers"

This is the riskiest idea in the plan, so it comes last and is optional.

- **Confirmation bias:** models trained on noisy machine labels overfit to the errors (Arazo et al., shown on images, so an analogy here).
- **Poisoning:** email is attacker-controlled. Crafted emails can steer LLM labels, and those labels then enter training. Every future email is affected, long after the attack email is gone.
- **Correlated errors:** the LLM saw the same misleading text, so its agreement is weak evidence. Snorkel-style approaches model noisy sources, but they assume more than one source of labels.
- **Privacy:** training data retains email content. A trained model may also carry information across project scopes, which conflicts with scope isolation.
- **Not every model improves the same way.** SetFit can retrain. For GLiNER, label wording and thresholds are the realistic levers, not retraining.

**Decision:** no automatic online learning. LLM suggestions go to a human review queue. Retraining is offline and versioned, and a model is promoted only after beating the current one on the frozen test set.
Yes the LLM will not improve nothing but maybe we can add a improvement iteration if the user finds a difference or maybe we can make the LLM predict based on the extracted data of the workers if any important information is missing

### C3 and "staying true to the architecture"

Being true to the architecture does not automatically mean being better, and C3 is not required by it: `ContextRetriever` is already part of the architecture. The Salemi et al. results are from data lakes, not email, and with few sources C3 may behave like C1 at higher cost. We benchmark this rather than assume it.

Concrete conflicts with the current contracts:

| # | Problem | Handling |
| --- | --- | --- |
| 1 | `AgentRegistry.all()` mixes all agents, so context agents could become reply candidates | Filter by `AgentConfig.capabilities` |
| 2 | `AgentBlackboard` lets every agent call `submitDraft` | Reject submissions from agents without a drafting capability |
| 3 | **Confused deputy:** a broad context agent fetches for a specialist with fewer rights | Evidence permissions must be the intersection of source and requester rights. `ContextQuery.agentId` must mean the requester |
| 4 | `Task` has no field for the information need, only `reason` | Add a structured need; do not parse free text |
| 5 | `sufficientContext` in `AgentAssessment` means something different for a context agent | Define it in Step 4.2, and do not overload it silently |
| 6 | `ModelRequest` has no response schema | Add one, or constrained decoding is impossible |
| 7 | `Analysis` has no per-field confidence or source, so "hard samples" cannot be identified | Extend it |
| 8 | Each volunteer consumes rounds from `ExecutionLimits` | Budget tests, and define what happens when context runs out |
| 9 | Corrections live in evidence while `Analysis` stays unchanged | One resolver defines the effective value |


We implement both and benchmark them

### Project-level risks

- **Hidden scope.** C3 needs a working coordinator, blackboard and registry, none of which exist. That is a large part of this plan.

It is not to me to implement them but when they are really missing I allow you to implement them as intermediates so we have a working prototype. Comment these implementations so that when my coworker works on it his agents know that these things are nessecary for our implementations to work
- **Two languages.** Training SetFit is Python, while the runtime is Java. ONNX in Java is unproven here, and the `inference4j` GLiNER route looks young. Step 1.5 tests this before anything depends on it.

- **Data.** I found no labeled university email corpus. Enron is corporate and old, and synthetic mail reflects its author's assumptions. Real mail raises privacy issues.
When we come to benchmarking we will find a database (maybe my own University mails)
- **Small test set.** With about 120 test emails, small gains will not be statistically distinguishable.
Yes we will test on small and on big ones
- **Three models plus a loop is a lot to explain and debug.** If time runs short, use the cut line in section 7.
We have 2 weeks for this project
- **Contract drift.** Each contract change also requires updating the UML and docs.
That is no problem but you dont do it it will be done at the end
## 4. Step-by-step plan

Each step ends with passing tests. Each phase ends with a gate. After each step you add a detailled report .md where All implementations are justified and results are discussed

### Phase 0: Decisions and foundations
- **0.1** Decide email languages and target hardware. *These affect model choice and are still open.*
- **0.2** Add JUnit 5 to `pom.xml`, with one trivial test.
- **0.3** Install the toolchain: JDK 17, Maven and Ollama are not on this machine.
- **0.4** Write the field schema: facts (sender, recipients, date, thread), entities (person, course, date, deadline, ID), labels (request, propose, commit, deliver, plus a role hint). State for each whether it is fact, entity or label.
- **0.5** Write the annotation guide.
- **0.6** Build the labeled set (section 5), and freeze the test split before any model work.
- **0.7** Build the benchmark runner, with a trivial baseline that predicts nothing.

**Gate:** the runner produces a report for the baseline.

### Phase 1: Metadata without an LLM
- **1.1** Extend `Analysis` with per-field value, confidence and source. Update the UML and docs.
- **1.2** Parse MIME and headers with Apache Tika / mime4j into `Email`.
- **1.3** Thread by `References` and `In-Reply-To` (RFC 5256), with subject only as a labeled fallback.
- **1.4** Strip quotes and signatures using rules; evaluate them on the zone-annotated Quagga data.
- **1.5** **Spike:** run GLiNER and a SetFit model through ONNX from Java on one email. Timebox it. Fallback order: a local Python sidecar, then rules and LLM only.
- **1.6** Implement entity extraction behind an internal interface.
- **1.7** Train SetFit offline on the dev split and export to ONNX. Keep the training scripts separate from the Java runtime.
- **1.8** Implement label classification.
- **1.9** Combine into `Preprocessor.analyze`.
- **1.10** Benchmark M1+M3 and set the hard-sample rule from dev data.

**Gate:** beats the baseline, and facts are correct without any model.

### Phase 2: Minimal blackboard runtime
- **2.1** In-memory `Blackboard` and `AgentBlackboard`, with defensive snapshots.
- **2.2** Enforce permissions in `accessFor`, `submitEvidence` and `submitDraft`.
- **2.3** `AgentRegistry` with capability filtering.
- **2.4** `Coordinator.receive`, then a `run` loop with budget enforcement and a scripted `CoordinationStrategy`.
- **2.5** Test scope isolation with equal thread IDs in different projects.

**Gate:** a fake agent completes a full loop and every run stops within its budget.

### Phase 3: LLM checker (M3+)
- **3.1** Add a response schema to `ModelRequest`. Update the UML and docs.
- **3.2** Ollama `LocalLlm` adapter: local-only endpoint check, timeout, no redirects, temperature 0.
- **3.3** Handle connection failure, timeout and invalid output explicitly.
- **3.4** Benchmark a model shortlist on hardware from 0.1.
- **3.5** Make the checker prompt: trusted instructions, fixed output values, and null when there is no evidence.
- **3.6** `MetadataCheckerAgent`: verify extracted items on hard samples only.
- **3.7** Add a blind extraction pass on hard fields, then compare with M3.
- **3.8** Effective-value resolver, with an allowlist of fields the LLM cannot change.
- **3.9** Coordinator runs `RECLASSIFY` before `ROUTE`; if the checker fails, continue with M3 hints.
- **3.10** Benchmark all variants and run the injection suite.

**Gate:** net benefit (rescue greater than damage) and security tests pass. **If not, keep M1+M3 and drop the checker. That is a valid result to report.**

### Phase 4: Context agents (C3)
- **4.1** Add a structured information need to `Task`. Update the UML and docs.
- **4.2** Define `assess` for context agents in writing, including `sufficientContext`.
- **4.3** Index one test source with Lucene BM25 and label the relevant documents for test queries.
- **4.4** Build `ContextRetriever.retrieveThread` and `retrieveDomain`.
- **4.5** Benchmark plain retrieval first: **this is the C1 baseline.**
- **4.6** Build one `ContextAgent` with deterministic `assess` and no LLM.
- **4.7** Enforce permission intersection and test the confused-deputy case.
- **4.8** Add a second source and agent; the coordinator chooses among volunteers.
- **4.9** Add `ContextSelector` with the budget.
- **4.10** Add bounded supplemental retrieval when context is insufficient.
- **4.11** Benchmark C3 vs C1.
- **4.12** Only if it helps: LLM-based `assess` or query rewriting, then benchmark again.

**Gate:** zero isolation violations, and a written C3 vs C1 result, including if C1 wins.

### Phase 5: Optional human-reviewed improvement loop
Only after the Phase 3 gate.
- **5.1** Store checker disagreements in a review queue, with personal data handled deliberately.
- **5.2** A person confirms or rejects labels.
- **5.3** Retrain SetFit offline and version the model.
- **5.4** Tune GLiNER labels and thresholds on dev data.
- **5.5** Promote only if it beats the current model on the frozen test set.
- **5.6** Run the poisoning experiment (section 5).
- **5.7** Optionally compare human labels with unreviewed LLM labels, as an experiment only.

## 5. Benchmarks

### Data
| Set | Use | Source |
| --- | --- | --- |
| Dev, about 80 emails | Tuning, SetFit training, thresholds | Synthetic university mail + Enron |
| Test, about 120 emails, frozen | Final numbers only | Same mix, stratified |
| Zones | Segmentation | Quagga annotated data |
| Speech acts | Label sanity check | De Felice et al. |
| Retrieval | Context | Own test sources with hand-labeled relevant documents |
| Attacks | Security | Own emails, with attack types modeled on AgentDojo |

Have a second person annotate about 20% of the data and report agreement. If the test set guides a decision, it is no longer a test set.

### Metadata variants
| Variant | Tests |
| --- | --- |
| Rules only | Floor |
| M3 only | Value of small models |
| M2, LLM only | The main alternative |
| M3 + check all | Cost of checking everything |
| M3 + check hard samples | Proposed design |
| M3 + blind pass on hard fields | Anchoring and recall |

### Metadata metrics
- Per-field precision, recall and F1, plus macro F1.
- Null accuracy: correctly returning nothing when no evidence exists.
- **Rescue rate:** wrong became right. **Damage rate:** right became wrong. **Pass-through rate:** wrong stayed wrong.
- Calibration: whether the hard-sample rule really catches errors.
- Latency p50 and p95, and LLM calls per email, on stated hardware.

### Context comparison
Compare thread only, C1, and C3, using:
- Recall@k, MRR and nDCG@k.
- Sufficiency decision accuracy.
- Rounds, LLM calls, and latency.
- **Hard requirement: zero** cross-scope or unauthorized evidence.

### Security and robustness
- Embedded instructions: change label, change sender role, request other-scope context, approve or send.
- Report attack success rate per attack type, and also check normal performance under the defense.
- Fault injection: model offline, timeout, invalid output, exhausted budget.

### Improvement loop
- Compare before and after on the frozen test set, by field.
- Poisoning: inject a known share of crafted emails into training and measure the attack's effect at several rates.

### Statistics
- Use McNemar's test for paired right/wrong comparisons (Dietterich), and bootstrap confidence intervals for F1.
- Report wide intervals honestly. Say "no measurable difference" when that is the result.

### Provisional targets
These are my starting values, not from the literature. Fix them before running the test set.
- The checker's rescue rate must exceed its damage rate, with a confidence interval not spanning zero net gain on hard fields.
- C3 must beat C1 on Recall@k or sufficiency by enough to justify its extra cost, with no isolation violations.

## 6. Main sources

Read level: **abstract** unless noted. Newer preprints are not peer reviewed.

### Your hybrid and checker
- Ma et al., [LLM Is Not a Good Few-shot Information Extractor, but a Good Reranker for Hard Samples](https://arxiv.org/abs/2303.08559), EMNLP Findings 2023. *Main source for the design.*
- Wang et al., [GPT-NER](https://arxiv.org/abs/2304.10428). Self-verification step.
- Dhuliawala et al., [Chain-of-Verification](https://arxiv.org/abs/2309.11495). Independent checks.
- Zaratiana et al., [GLiNER](https://arxiv.org/abs/2311.08526). 2023 preprint.
- Tunstall et al., [SetFit](https://arxiv.org/abs/2209.11055), with its [ONNX tutorial](https://huggingface.co/docs/setfit/tutorials/onnx).
- [inference4j GLiNER model](https://huggingface.co/inference4j/gliner-small-v2.1). Model page only; maturity unknown.

### Improving small models
- Arazo et al., [Pseudo-Labeling and Confirmation Bias](https://arxiv.org/abs/1908.02983). Image data.
- Ratner et al., [Snorkel](https://arxiv.org/abs/1711.10160).
- [Generative clean-label backdoor attacks on text classifiers](https://arxiv.org/pdf/2310.18603), EMNLP Findings 2023.

### Parsing and segmentation
- [RFC 5256](https://www.rfc-editor.org/rfc/rfc5256.html). Threading algorithm, read directly.
- [Apache Tika `RFC822Parser`](https://tika.apache.org/4.0.0/api/org/apache/tika/parser/mail/RFC822Parser.html).
- Repke & Krestel, [Quagga](https://github.com/TimRepke/Quagga), ECIR 2018. Annotated data and code.
- Bevendorff et al., [Chipmunk](https://aclanthology.org/2020.acl-main.108/), ACL 2020.

### Email labels
- Cohen, Carvalho & Mitchell, [Email speech acts](https://aclanthology.org/W04-3240/), 2004.
- De Felice et al., [Annotated speech act corpus](https://discovery.ucl.ac.uk/1395694/1/DeFelice_etal_2013.pdf), 2013.
- Zhang et al., [EmailSum](https://aclanthology.org/2021.acl-long.537), ACL 2021. Intent and roles are hard.

### Local structured output
- Chavan, [Constrained Decoding in Small LLMs](https://arxiv.org/html/2609.23742v1), 2026. Single-author preprint; results table read.
- [Ollama structured outputs](https://docs.ollama.com/capabilities/structured-outputs).

### Blackboard and retrieval
- Salemi et al., [Blackboard System for Information Discovery](https://arxiv.org/abs/2510.01285v2), 2025. *Main source for C3; data lakes.*
- Han & Zhang, [LbMAS](https://arxiv.org/html/2507.01701v1), 2025.
- Cormack et al., [Reciprocal Rank Fusion](https://research.google/pubs/pub36196/), SIGIR 2009. Only if embeddings are added.

### Security and evaluation
- Beurer-Kellner et al., [Design Patterns for Securing LLM Agents](https://arxiv.org/abs/2506.08837), 2025.
- Debenedetti et al., [CaMeL](https://arxiv.org/abs/2503.18813), 2025.
- Debenedetti et al., [AgentDojo](https://arxiv.org/abs/2406.13352), 2024.
- Dietterich, [Approximate Statistical Tests](https://web.engr.oregonstate.edu/~tgd/publications/dietterich-approximate-statistical-tests-nc1998.pdf), 1998.

### Evidence gaps
I found no study of this pipeline on university email, and none on anchoring in LLM checkers or on poisoning through email feedback. Those risks above are reasoning from nearby work, which is why they are tested here.

## 7. Cut line if time runs short

Cut in this order: Phase 5, then step 4.12, then step 3.7. Keep Phase 2 and the benchmark: without them there is neither an architecture nor a result to report.

## 8. Verification of this plan document

After saving, check that all links open and that every contract named here exists in `src/main/java/org/supermas`.
