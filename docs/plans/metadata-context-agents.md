# Metadata and Context Agents: Implementation and Benchmark Plan

**Status:** Phases 0-4 and 8-10 are implemented (reports in `docs/reports`). Phases 8-10 were added on 2026-11-03 and Phase 11 on 2026-11-06, both approved by the project owner. Steps 11.0-11.6 are done on the `prototype` branch. Decisions still open are listed in section 2.

## 1. Context and scope

We have **two weeks** to build the `MetadataAgent` and the `ContextAgent` part of a three-person project, and to benchmark them. The top priority is staying true to the blackboard architecture.

| Ours to implement (`CLAUDE.md`) | Not ours (`CLAUDE.md`) |
| --- | --- |
| `MetadataAgent`, `ContextAgent`, memory and retrieval, source relevance | Blackboard storage, scheduling, global state machine |
| Specialized agents, prompts, skills, configuration | Central routing, global events, timeouts and errors |
| Structured contributions for the blackboard | Local LLM integration, final email writing, UI, sending |
| | Changing shared interfaces alone: all three members must validate them |

**Who decides:** the project owner alone decides everything in the left column. Only shared interfaces and the right column need the team.

**This list is why the plan changed.** The earlier version had us building a coordinator and an Ollama adapter, and changing shared records. Section 5 now treats those as marked prototype stand-ins or team proposals.

## 2. Decisions

### Made

| Topic | Decision | Effect |
| --- | --- | --- |
| Email languages | English and French, but **English only for now** while French real data is blocked | French work (analyzers, labeled French data, per-language French results) is deferred, not removed. MIME parsing stays language-agnostic |
| Metadata | Rules first, then GLiNER and SetFit | Phase 1 |
| LLM checker | Set aside, kept as a switch that is **off by default**, benchmarked later | Phase 5 |
| Improving the workers | No LLM-driven learning in the product. Optional iteration from user corrections | Phase 6 |
| Labelling benchmark and training data | An LLM labels public data. A person reviews the flagged dev labels and hand-labels a random 25 test emails without seeing the LLM's labels. Agreement is reported with every accuracy result. Test labels are frozen before any model runs on those emails | Section 6; `docs/reports/labeling-setup.md` |
| Missing information | Predict what is missing from extracted data | Phase 4 |
| Context | Implement both C1 and C3 and benchmark them | Phases 3 and 4 |
| Missing runtime parts | Build only what is really missing, as commented stand-ins for the coworker's agents | Phase 2 |
| Benchmark data | Enron plus personal mail (Debian and Webis dropped). Test on a small and a big set | Section 6 |
| UML and docs for contract changes | Not during implementation; all done at the end | Step 7.3 |
| Reporting | A detailed `.md` report after each step | Section 5 |
| Topic design | T2 + O2 | Section 4 |
| Correspondent topics | May read across threads only within the same project and only for verified senders. Switch off by default | Step 4.9; update `docs/architecture.md` in step 7.3 |
| Proposals 6 and 7 (section 3) | Accepted by the project owner | Steps 3.3 and 4.1 |
| Topic discovery (T3) | **Changed 2026-11-03:** subject topics are discovered and regrouped automatically, with no action from the user and no approval step | Phase 10. This replaces the "a person approves" form in section 4 |
| Specialized agents | One configurable class, roles defined in JSON files with their own prompt files. A specialist writes a structured reply plan, then asks the team's `GenerationService` for the draft | Phase 9 |
| Memory | Conversation history is stored in the per-project index, kinds kept separate, and `SENT` only after a confirmed send | Phase 8 |
| Hardware | This laptop for development (i5-8265U, 7 GB RAM, no dedicated GPU), better machines later | Small models stay local |
| LLM during development | A small-scale online LLM is accepted, because this laptop is too weak. Switching to a local one must be easy | Section 3, step 5.1 |
| `AgentContribution` | Planned by the team, not ours to define | Use `Evidence` and `AgentOutcome` until it arrives, then adapt |

### Open

| # | Question | Default until answered |
| --- | --- | --- |
| 1 | **Shared interface changes (team decision).** The project owner accepts proposals 1-5, 8 and 9 in section 3. The other two members validate them later | Work proceeds as if validated, using the rules in section 3 |

## 3. Target design and contract proposals

```
Coordinator.receive
  └─ MetadataAgent (implements Preprocessor): parse → segment → rules → GLiNER + SetFit
     no LLM by default

Coordinator.run: one action at a time
  ├─ optional checker       (off by default)
  ├─ RETRIEVE_CONTEXT       topic context agents volunteer through assess
  ├─ missing-information check → may request bounded further retrieval
  └─ ROUTE and GENERATE     not ours
```

Each pipeline stage has a switch in one configuration record, so any benchmark variant in section 6 runs without code changes.

**Online LLM during development.** The project's documented invariant is local-only inference, and `LocalLlm` and `ModelConfig` assume it. An online LLM is therefore an accepted, temporary development exception, with these rules:

- **No real mail goes online.** Only synthetic and public data (such as Enron) may reach it. Your university mail stays local, because sending it would pass other people's personal data to a third party. *Amended by the project owner on 2026-11-07 (step 13):* the agent service, which serves the mailbox, may use the online model only behind a second explicit opt-in, `SUPERMAS_LLM_ONLINE_MAIL=yes`.
- **It is off by default and explicit.** A configuration flag names the remote endpoint and is clearly marked as development only. The local-only check stays the default, so the later local deployment is not weakened.
- **The API key stays out of Git**, for example in `.env`, which is already ignored.
- **Online results do not carry over.** Accuracy and latency must be measured again on the final local model and hardware. A report must name the model and where it ran.
- **Use a model that can also run locally,** so the later move changes as little as possible.

**Switching to a local LLM must be a configuration change only:**

- Agents depend only on the `LocalLlm` interface and never on a provider.
- One adapter speaks the OpenAI-compatible chat API, which Ollama also offers. If the chosen online provider does not offer it, it gets a second small adapter behind the same interface.
- Endpoint and model come from `ModelConfig` values in configuration. The API key comes from the environment and is optional, because a local model needs none.
- Prompts and JSON schemas contain nothing provider-specific.
- A shared test suite runs against a fake server for both configurations, so structured output and failure handling stay equivalent. **Step 5.1 is done only when one real call to a local Ollama model has also worked**, on any machine able to run it.

**Limit:** this makes the *switch* easy, not the *results* identical. A local model may be weaker, so the benchmark must be re-run after switching.

**Proposals.** Numbers 6 and 7 are behavior inside our agents, so the project owner decides them; if `ContextQuery.agentId` is documented as shared, tell the team what it means. The rest change shared interfaces or team-owned parts and still need team validation, which comes later. Work proceeds as if it were given, under these rules so that any choice can be changed easily:

- **Marked in code.** Every place that depends on an unvalidated choice carries this comment, so one search finds it all:
  ```java
  // ASSUMED-TEAM-DECISION(P<number>): <the choice made>. Pending team validation.
  // If rejected: <what to change>.
  ```
- **One proposal, one commit.** Each proposal gets its own commit, so it can be reverted alone.
- **Listed in one place.** `docs/plans/assumed-decisions.md` lists each proposal with its status, commit and affected files. This is what goes to the team.
- **Isolated from agent logic.** Agents read these fields through small helpers where practical, so a rejected change touches few files.

Teammates may be coding against `Domain.java` too, so changed records can conflict with their work. Tell them before merging changes to it.


| # | Gap | Proposal | Current handling |
| --- | --- | --- | --- |
| 1 | `Analysis` has no per-field confidence or source | Add a field record | Change the record, marked |
| 2 | `Task` has no information need | Add a structured need | Change the record, marked |
| 3 | `ModelRequest` has no response schema | Add an optional schema | Change the record, marked |
| 4 | Agents in `AgentRegistry.all()` could become reply candidates | Filter on `AgentConfig.capabilities` | Stand-in registry filters |
| 5 | Every agent can call `submitDraft` | Reject it unless the agent has a drafting capability | Stand-in blackboard rejects |
| 6 | Confused deputy: a broad context agent fetches for a specialist with fewer rights | Evidence rights are source rights ∩ requester rights; `ContextQuery.agentId` means the requester | Enforced in our retriever |
| 7 | `sufficientContext` means something else for a context agent | Define it in writing | Documented in the `ContextAgent` |
| 8 | Each volunteer uses up `ExecutionLimits` rounds | Agree how context budget is counted | Test in the stand-in |
| 9 | `Feedback` covers drafts, not metadata | Add a metadata correction record | Add the record, marked; local file for storage |

## 4. Context agents: one agent per topic

You proposed one agent per topic, with possible overlap, where a topic may be a discussion, a sender or something more general. There are two separate decisions.

### Decision A: what defines a topic

| | T1: Declared topics | **T2: Three topic types (recommended)** | T3: Discovered topics |
| --- | --- | --- | --- |
| **How** | People write each topic in configuration, as filters: keywords, tags, sources | One `TopicContextAgent` class with three types: **discussion** (thread), **correspondent** (sender), **subject** (declared, such as a course or project) | Cluster past mail, for example with BERTopic, with one agent per cluster |
| **Fits because** | Matches "add agents without changing the orchestrator" and the existing `AgentConfig.keywords` and `contextSourceIds` | Closest to your definition. Overlap is natural: one email belongs to all three | Finds topics nobody thought to declare. Personal-topics research supports clustering email for this |
| **Downsides** | Misses anything undeclared; manual upkeep | Sender type causes the security problems below. Discussion type partly duplicates thread history | Python-only tool, unstable cluster IDs, weak on small mailboxes, hard to explain, and clusters can be steered by incoming mail |
| **Two-week effort** | Small | Small to medium | Too large; later experiment |

### Decision B: how overlap is handled

| | O1: Separate store per agent | **O2: Shared index, agents as filtered views (recommended)** | O3: Single-owner topics |
| --- | --- | --- | --- |
| **How** | Each topic indexes its own copy | One Lucene index per project; an email carries many topic tags; agents query with a filter | Assign each email to its best topic |
| **Upside** | Simple isolation | Overlap costs nothing; re-tagging needs no re-indexing; one document has one evidence ID | No duplicates at all |
| **Downside** | Duplicates, drift between copies | One filter bug can leak data across topics, so it needs strong tests | Contradicts your overlap requirement and the findings below |

**Research support for overlap:** Joty et al. found on average 2.5 topics per thread in their email corpus, not always in sequence, so a thread and a topic are not the same thing. BuzzTrack also lets one email join several topics.

**Merging overlapping results, in `ContextSelector`:**
1. Remove duplicates by evidence ID, keeping every topic that found it as provenance.
2. Merge rankings with Reciprocal Rank Fusion, which requires no score normalization.
3. Use Maximal Marginal Relevance so the context budget does not fill with near-duplicates.

### Problems with one agent per topic

1. **Sender topics can leak data, and this is the most serious issue.** A correspondent agent reads across threads, which current docs forbid without explicit permission. A sender's other threads may hold information that should not reach this reply.
2. **Sender identity can be forged.** The `From` header is not proof of identity. DMARC checks the domain only, and not the person. A forged sender could pull a victim's history into a draft. So a correspondent topic needs a verified-sender signal, or it stays off.
3. **Are these really agents?** With deterministic filters, a topic agent is close to a saved query. If it adds no judgment, C3 may tie C1 at higher cost. This is the key question for the benchmark, and the assignment names one `ContextAgent`, so check with your team that many instances are acceptable.
4. **Number of agents.** One per sender and thread could mean thousands, and every `assess` call and round costs budget. Candidates must be filtered first, using only thread and sender match plus the top few subjects. Topic agents should be instantiated when needed.
5. **Trust boundary.** `AgentConfig` holds *trusted* prompts. Generated topics must come from a fixed template, and email text must never enter their configuration or prompts.
6. **Duplicates inflate confidence.** Three agents finding one email is one piece of evidence, not three.
7. **Cold start.** New senders and new topics have no history, so "no context" must be a normal outcome.
8. **Language.** A topic spans English and French, and BM25 with one analyzer per language will not match across them. Cross-language matches may require embeddings.

### Recommended design

Use T2 + O2 in this order:
1. Subject and discussion types.
2. Correspondent type behind a switch that is off by default: same project and verified senders only.
3. Merge step.

### Possible later experiment: T3 discovered topics

T3 is not in the two-week scope and nothing depends on it. If time allows after the project, or as an extension:

- **Purpose:** find useful topics nobody declared, and test whether they improve retrieval over T2.
- **Safest form:** clusters only *suggest* new subject topics, and a person approves them. Approved topics become ordinary T2 subject topics, so no agent design changes.
- **Method:** run BERTopic offline in Python with a multilingual embedding model.
- **Measure:** T2 alone vs T2 + approved suggestions, using the context metrics in section 6, plus how stable clusters are between runs.
- **Risks:** weak results on small mailboxes, and incoming mail could steer clusters.

## 5. Step-by-step plan

**For every step:** one behavior, passing tests, one commit, and a report at `docs/reports/step-X.Y.md` giving what was built, why, the sources behind it, the results, and what went wrong. Reports stay short where there is nothing to discuss.

**Stand-ins:** anything outside our scope goes in `org.supermas.prototype`, with this comment on each class:

```java
// PROTOTYPE-STAND-IN: owned by the team's <area> work, not by the Metadata/Context agents.
// Required by these agents: <exact behavior they rely on>. Replace with the real implementation.
```

`docs/plans/stand-ins.md` lists them for your coworker's agents.

### Phase 0: Foundations (days 1-2)
- **0.1** Create `docs/plans/assumed-decisions.md`. Sending it to the team can wait, but tell the LLM integration owner that it must stay switchable.
- **0.2** Add JUnit 5 with one trivial test.
- **0.3** Install JDK 17 and Maven. Ollama is not needed on this laptop while the online LLM is used, except for the one local check in step 5.1 with a very small model.
- **0.4** Write the field schema: facts (sender, recipients, date, thread), entities (person, course, date, deadline, ID), labels (request, propose, commit, deliver, role hint).
- **0.5** Build the benchmark runner with a baseline that predicts nothing.
- **0.6** Create 20-30 synthetic English and French development emails so work can start without the final dataset.

**Gate:** the runner produces a report for the baseline.

### Phase 1: `MetadataAgent` without an LLM (days 2-5)
- **1.1** Parse MIME and headers with Apache Tika / mime4j.
- **1.2** Thread with `References` and `In-Reply-To` (RFC 5256), with subject only as a labeled fallback.
- **1.3** Rules to strip quotes and signatures, in English and French (such as `Le ... a écrit :` and `Cordialement`).
- **1.4** Language detection per email.
- **1.5** **Timeboxed spike, half a day:** run GLiNER multi and a SetFit model through ONNX from Java. Fallback: a local Python process, then rules only.
- **1.6** Entity extraction.
- **1.7** Train SetFit offline with a multilingual body and export to ONNX.
- **1.8** Label classification.
- **1.9** Combine into `MetadataAgent implements Preprocessor`, with per-stage switches.
- **1.10** Benchmark rules only vs rules + models, per language.

**Gate:** facts are right without a model, and models beat rules on entities and labels.

### Phase 2: Minimal stand-ins (day 6)
Only what is really missing at this point. Check first whether a teammate has delivered it.
- **2.1** In-memory blackboard with permission checks.
- **2.2** Registry with capability filter.
- **2.3** Scripted coordinator loop with a round limit.
- **2.4** Isolation test: equal thread IDs in different projects.

**Gate:** a fake agent completes a full loop and stops within budget.

### Phase 3: Retrieval and C1 baseline (days 7-8)
- **3.1** Per-project Lucene index with English and French analyzers.
- **3.2** `retrieveThread` with exact-scope matching.
- **3.3** `retrieveDomain` with permission intersection, plus a confused-deputy test.
- **3.4** Index topic tags.
- **3.5** Benchmark: **this is the C1 baseline.**

### Phase 4: Topic agents (C3) and missing information (days 9-11)
- **4.1** Define `assess` for context agents in writing.
- **4.2** `TopicContextAgent`, subject type, with deterministic `assess` and no LLM.
- **4.3** Discussion type.
- **4.4** Candidate filtering.
- **4.5** Merge: dedupe, RRF, MMR, within budget.
- **4.6** **Missing-information check with rules:** a table of what each label needs (a meeting request needs a date, for example).
- **4.7** **LLM variant behind a switch:** the LLM receives field *names and presence*, not raw email, and returns which required fields are missing from a fixed list.
- **4.8** Bounded further retrieval when something is missing.
- **4.9** Correspondent type behind a switch that is off by default: same project and verified senders only, with forged-sender and cross-project tests. Unverifiable senders get no cross-thread history.
- **4.10** Benchmark C3 vs C1.

**Gate:** zero isolation violations and a written C3 vs C1 result, including if C1 wins.

### Phase 5: Optional LLM checker (days 12-13, cut first)
- **5.1** Stand-in LLM adapter behind `LocalLlm`, with explicit failures. Local endpoint by default, online endpoint only through the development flag, and switching by configuration only (section 3).
- **5.2** Checker prompt using the anti-anchoring design below.
- **5.3** Allowlist of fields the LLM cannot change, and evidence-only output.
- **5.4** Benchmark on and off, plus the injection suite.

**Why this design:**
- **Is approving easier than extracting?** You are partly right. Ma et al. turn hard cases into multiple-choice questions over the small model's top candidates, because this is a simpler task. However, it cannot recover what the workers missed, and a yes/no format invites agreement. So we use multiple choice rather than yes/no.
- **Reducing anchoring** (my design proposals, tested in the benchmark, not proven methods):
  - Ask an open question first, without showing the workers' answer, then compare.
  - Offer shuffled options, including "none of these".
  - Require a verbatim quote, and have code verify that it exists in the email.
  - In benchmarks, add known-wrong candidates to measure the checker's tendency to say yes.

### Phase 6: Optional correction iteration (only if time remains)
- **6.1** Record a user's correction to a metadata field in a local file. The UI belongs to the team.
- **6.2** Retrain SetFit offline and version the model.
- **6.3** Promote only if it beats the current model on the frozen test set.

Corrections are human labels, which removes the LLM label risks. **Limitation:** an edited draft does not reveal which extracted field was wrong, so a draft diff alone cannot train the workers.

### Phase 8: Memory
- **8.0** Bring `CLAUDE.md` and this plan up to date.
- **8.1** `IndexConversationRepository` implements `ConversationRepository` over the project index, exact scope, kinds kept apart, idempotent append.
- **8.2** `ConversationRecorder`: received mail, generated and user-edited drafts, and `SENT` only for an approved version with a confirmed send.
- **8.3** The stand-in coordinator records each received email, so the next email in the thread sees it.
- **8.4** History on disk survives a restart.
- **8.5** Knowledge loading: trusted documents per source, with readers per source, from a folder.
- **8.6** Correspondent facts (optional, off by default). Cut first.

### Phase 9: Specialized agents
- **9.1** Define `assess` for reply agents in writing.
- **9.2** `RoleDefinition` and a JSON role catalogue with one prompt file per role. Adding a role is a file, not code.
- **9.3** `RoleAgent`: deterministic `assess` for routing; Student, Teacher, Research, Administration and Generic (fallback) are configuration.
- **9.4** `execute` writes a structured reply plan to the board, citing only evidence the agent may read.
- **9.5** Prompt building: trusted system prompt, untrusted data, input budget, then the draft through `GenerationService`.
- **9.6** Optional LLM extraction of the questions to answer, verified against the email, off by default.
- **9.7** Stand-in template generation service and an end-to-end test.
- **9.8** Routing and plan benchmark on the synthetic set.

### Phase 10: Discovered topics
- **10.1** Cluster a project's threads into topics with keywords, in Java, deterministic, with no user input.
- **10.2** Regroup automatically: stable topic identifiers across runs, index tags updated, and a refresh after enough new mail.
- **10.3** One discovered-topics context agent with a fixed configured identity, so discovery never grants rights.
- **10.4** Benchmark discovered topics against declared topics and C1 on the folder proxy of step 4.10.

### Phase 11: First working prototype (branch `prototype`, added 2026-11-06, approved by the project owner)
- **11.0** Branch `prototype` from `origin/main`; merge `docs/metadata-context-plan`, then `origin/codex/blackboard-storage` (which contains the desktop mailbox). `team-knowledge-vault` is read, never merged.
- **11.1** Move our imports to the teammate's `blackboard.api` and `blackboard.persistence` packages.
- **11.2** A coordinator stand-in on the durable SQLite board, using the teammate's code unchanged: leased tasks, board-made execution records, restarts.
- **11.3** Private agent contributions and coordinator promotion from trusted configuration and document readers.
- **11.4** `PrototypeApp`: `.eml` files to drafts awaiting review, with Markdown for Obsidian. Generation stays the template stand-in; a local model is the intended next step.
- **11.5** Reports, stand-in list, README, architecture and CLAUDE.md.
- **11.6** Connect the desktop mailbox to the Java prototype: a loopback agent service, and an opt-in branch in the mailbox's generate route. Routing becomes labels on clear matches; the mailbox UI is unchanged (project owner).

### Phase 12: A real blackboard (added and approved 2026-11-07)
- **12.0** Design: five domain levels, a BB1 control blackboard, Corkill's estimates (`docs/plans/blackboard-control.md`).
- **12.1** Teammate's board: read-only views, feedback, drafting capability, kept needs; `Action.CONTRIBUTE`.
- **12.2** Request splitter. **12.3** Specialists post contributions. **12.4** Agenda controller. **12.5** Composer.
- **12.6** Revision after rejection. **12.7** Benchmark router vs blackboard. **12.8** Reports and docs.
- Plus a local-model adapter for the OpenAI-compatible API (template stays the default). Report: `docs/reports/step-12.md`.

### Phase 13: Online model for development, local for delivery (added and approved 2026-11-07)
- `LlmSettings` (template, local, online, replay) from environment or flags; the online adapter for OpenRouter (HTTPS, key from the environment, second opt-in for the mailbox); `RecordedLlm` record and replay; setup guide `docs/plans/llm-setup.md`. Report: `docs/reports/step-13.md`.

### Phase 7: Finish (day 14)
- **7.1** Final benchmark, on the frozen data.
- **7.2** Final report, with C3 vs C1 and every switch combination run.
- **7.3** Apply the team's validation results by finding every `ASSUMED-TEAM-DECISION` marker. Update UML and docs for validated contract changes, and the thread isolation rule in `docs/architecture.md` for correspondent topics.

## 6. Benchmarks

### Data

The sources are **Enron and personal mail** (decision by the project owner; the Debian and Webis options were dropped). The Enron importer is built (`docs/reports/data-import-enron.md`); personal mail is blocked, see below.

| Set | Purpose | Note |
| --- | --- | --- |
| **Small, labeled** (200 emails from all 150 Enron mailboxes) | Accuracy numbers | 100 dev and training emails labelled by an LLM, with flagged labels reviewed by a person; 100 frozen test emails labelled by an LLM, with 25 audited by hand |
| **Private real** (personal mail) | Final check, English and French | Totals only in reports, no email content; currently blocked |
| **Big** (Enron, 10,000+) | Speed, robustness, retrieval at scale | Without labels it **cannot give accuracy** |
| Quagga zones | Segmentation | English only |
| De Felice speech acts | Label sanity check | English only |
| Attack emails | Security | Types modeled on AgentDojo |

**Data problems to settle:**
- **Timing.** SetFit needs labeled training mail in Phase 1, so the Enron labeling must start early. Step 0.6's synthetic set reflects its author and is development-only.
- **French is the main gap.** Enron is English only. French real data was to come from personal mail, which is blocked (below), so French accuracy currently rests on the synthetic set alone. If the mailbox stays inaccessible, reconsider a public French source.
- **Personal mail is blocked.** The Microsoft 365 export did not work; the administrator has been asked. Legitimate routes: a scoped request to the admin, Outlook-on-the-web export, or forwarding selected mail to a personal account. Do not attempt to bypass the block.
- **Personal mail contains other people's personal data.** Keep it in an ignored folder (`.gitignore` covers `benchmark/data/personal/` and similar), never send it to the online LLM, and put only totals in reports. LLM variants cannot be benchmarked on it until a local model exists. **Ask your supervisor or the university's data protection contact before using it.**

### Metadata variants
| Variant | Answers |
| --- | --- |
| Rules only | Floor |
| Rules + GLiNER + SetFit | Default design |
| LLM only | Your "checker only" idea, as the main alternative |
| Rules + models + checker, hard samples | Does the checker add value? |
| Same, multiple choice vs yes/no | Is approving easier? Is it anchored? |

**Metrics:**
- Per-field precision, recall, F1, and correct nulls, per language.
- Rescue, damage and pass-through rates for the checker.
- Latency p50 and p95, and memory, on stated hardware.

### Context variants
Compare thread only, C1, C3 without correspondent, and C3 with correspondent. Also compare rules vs LLM for the missing-information check.

**Metrics:**
- Recall@k, MRR, nDCG@k; duplicate rate after merging.
- Topic assignment F1 (multi-label).
- Missing-information precision and recall.
- Agents activated, rounds, LLM calls and latency per email.
- **Hard requirement: zero** cross-scope or unauthorized evidence, including a forged-sender test.

### Security and robustness
- Embedded instructions that try to change a label, change a sender role, fetch other-scope context, or approve or send.
- Failure tests: model offline, timeout, invalid output, budget exhausted.

### Statistics
Use McNemar's test for paired right/wrong comparisons and bootstrap intervals for F1. Say "no measurable difference" when that is the result. Fix target thresholds before running the frozen test set.

## 7. Project-level risks

- **Two weeks is tight.** This schedule has no slack. If behind, cut in this order: Phase 6, Phase 5, step 4.9, step 4.7. Do not cut Phases 1, 3, 4.1-4.5 or the benchmark.
- **Hardware.** GLiNER and SetFit still run on this laptop with limited memory, and I have not measured them. Step 1.5 will show whether they are fast enough.
- **Online vs local gap.** A design that works with the online LLM may fail on a local one. Keep the LLM optional and re-run the benchmark on the final machine, and leave time for this before the deadline if those machines are available.
- **Team dependency.** If teammates' parts arrive with other assumptions, the stand-ins may not match. Agree on interfaces in step 0.1, not on day 12.
- **Docs at the end.** Leaving UML until the end is fine, but `CLAUDE.md` says to update them with each contract change. Add a note there so future agents follow this decision.
- **Report overhead.** A detailed report for about 40 steps takes real time, which is why reports stay short when nothing notable happened.

## 8. Main sources

Read level: **abstract or summary** unless noted. Newer preprints are not peer reviewed.

### Metadata
- Zaratiana et al., [GLiNER](https://arxiv.org/abs/2311.08526), 2023 preprint; [GLiNER multi model](https://huggingface.co/urchade/gliner_multi-v2.1), multilingual but French is not named, so test it.
- Tunstall et al., [SetFit](https://arxiv.org/abs/2209.11055); [ONNX tutorial](https://huggingface.co/docs/setfit/tutorials/onnx).
- [Multilingual sentence embedding model](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2), 50 languages listed, French not named on the page.
- [inference4j GLiNER model](https://huggingface.co/inference4j/gliner-small-v2.1). Maturity unknown.
- [RFC 5256](https://www.rfc-editor.org/rfc/rfc5256.html), threading, read directly.
- [Apache Tika `RFC822Parser`](https://tika.apache.org/4.0.0/api/org/apache/tika/parser/mail/RFC822Parser.html).
- Repke & Krestel, [Quagga](https://github.com/TimRepke/Quagga), ECIR 2018; Bevendorff et al., [Chipmunk](https://aclanthology.org/2020.acl-main.108/), ACL 2020.
- Cohen, Carvalho & Mitchell, [Email speech acts](https://aclanthology.org/W04-3240/), 2004; De Felice et al., [Speech act corpus](https://discovery.ucl.ac.uk/1395694/1/DeFelice_etal_2013.pdf), 2013.
- Zhang et al., [EmailSum](https://aclanthology.org/2021.acl-long.537), ACL 2021.

### Checker and corrections
- Ma et al., [LLM Is a Good Reranker for Hard Samples](https://arxiv.org/abs/2303.08559), EMNLP Findings 2023. Multiple-choice prompt format checked in the full text.
- Dhuliawala et al., [Chain-of-Verification](https://arxiv.org/abs/2309.11495); Wang et al., [GPT-NER](https://arxiv.org/abs/2304.10428).
- Chavan, [Constrained Decoding in Small LLMs](https://arxiv.org/html/2609.23742v1), 2026, single-author preprint; [Ollama structured outputs](https://docs.ollama.com/capabilities/structured-outputs).
- Arazo et al., [Confirmation Bias](https://arxiv.org/abs/1908.02983), image data; Ratner et al., [Snorkel](https://arxiv.org/abs/1711.10160); [LLM clean-label attacks](https://arxiv.org/pdf/2310.18603). These explain why LLM-driven learning was dropped.

### Topics, retrieval and blackboard
- Salemi et al., [Blackboard System for Information Discovery](https://arxiv.org/abs/2510.01285v2), 2025, on data lakes; Han & Zhang, [LbMAS](https://arxiv.org/html/2507.01701v1), 2025.
- [Automatic Discovery of Personal Topics to Organize Email](https://www.microsoft.com/en-us/research/wp-content/uploads/2017/03/PersonalTopics.pdf), Microsoft Research.
- [BuzzTrack: Topic Detection and Tracking in Email](https://www.academia.edu/251109/BuzzTrack_Topic_Detection_and_Tracking_In_Email).
- Joty et al., [Exploiting Conversation Structure in Unsupervised Topic Segmentation for Emails](https://www.cs.ubc.ca/~carenini/PAPERS/emnlp10.pdf), EMNLP 2010. 2.5 topics per thread, checked in the full text.
- Grootendorst, [BERTopic](https://arxiv.org/abs/2203.05794).
- Cormack et al., [Reciprocal Rank Fusion](https://research.google/pubs/pub36196/), SIGIR 2009; Carbonell & Goldstein, [MMR](https://people.eng.unimelb.edu.au/ammoffat/sigir98/abstracts/carbonell.html), SIGIR 1998.
- [Lucene `FrenchAnalyzer`](https://lucene.apache.org/core/9_12_0/analysis/common/org/apache/lucene/analysis/fr/FrenchAnalyzer.html), read directly; check the version we adopt.

### Security and evaluation
- Beurer-Kellner et al., [Design Patterns for Securing LLM Agents](https://arxiv.org/abs/2506.08837), 2025; Debenedetti et al., [CaMeL](https://arxiv.org/abs/2503.18813), 2025; [AgentDojo](https://arxiv.org/abs/2406.13352), 2024.
- [RFC 7489: DMARC](https://www.rfc-editor.org/rfc/rfc7489.html), informational, domain-only checks, first 100,000 characters read.
- Dietterich, [Approximate Statistical Tests](https://web.engr.oregonstate.edu/~tgd/publications/dietterich-approximate-statistical-tests-nc1998.pdf), 1998.

### Evidence gaps
I found no study of this pipeline on university or French email, none comparing per-topic agents to a single retriever for email, and none on anchoring in LLM checkers. Those points are reasoning from nearby work, which is why the benchmark tests them.
