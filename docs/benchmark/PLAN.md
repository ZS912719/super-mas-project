# Benchmark Plan

Agreed with the owner on 2026-11-11. Every benchmark of the project, on three levels: **components**, **parts**, and the **whole system**. Each has an ID; its runs are recorded in [`PROTOCOL.md`](PROTOCOL.md), whose entries name the plan ID. Statuses here are updated when a run is recorded.

Legend: **done B-nn** = a protocol entry exists; **re-run** = the code or the data changed since; **model** = needs the online model (OpenRouter, the same open-weights model that would run locally); **new** = needs a small new harness.

## Data

| Set | Size | Use |
| --- | --- | --- |
| Enron test (frozen 2026-11-11, protocol B29) | 100: 25 hand-labelled blind, 54 LLM-reviewed, 21 LLM | Metadata; final numbers. The 25 hand labels are always reported separately as well |
| Enron dev | 100, LLM labels, unreviewed | Tuning only |
| Enron maildir | 7 mailboxes, 10,206 messages | Retrieval, memory, threading, parser robustness |
| `synthetic-dev`, `synthetic-multi`, knowledge | 24 + 12 emails, 6 documents | Routing, collaboration, replies |
| **Fresh held-out set (to write)** | about 24 emails | Written before tuning results are seen, ideally by a teammate; final whole-system run (W8, to-do 3.6) |

## Level 1: components

| ID | Component | Question and metric | Data | Status |
| --- | --- | --- | --- | --- |
| C01 | `MimeEmailParser` | Parse rate, warnings, ms per email | Enron maildir | done B46 |
| C02 | `EmailThreader` | Thread-ID accuracy, wrong merges | synthetic, Enron (`ThreadingStats`) | done B47 (synthetic); Enron has no reply headers (B46) |
| C03 | `QuoteSignatureStripper` | `currentText` exact match and F1 | Enron test | done B30 (test); B03 on dev |
| C04 | `LanguageDetector` | Accuracy en / fr | synthetic, Enron | done B30 (test), B02 |
| C05 | Entity extractors: rules, GLiNER, hybrid | F1 per entity type | Enron test | done B31 (test); B04 on dev |
| C06 | Speech-act classifiers: rules, ONNX | F1 per act | Enron test | done B32 (test); B05, B06 on dev |
| C07 | `RuleRoleHintClassifier` | Precision, recall | synthetic | done B34 (confusion matrix still open) |
| C08 | `LuceneContextRetriever` (C1) | Recall@k, MRR, nDCG against recency | Enron maildir | done B38 (= B08) |
| C09 | Topic agents and discovered topics | As C08, thread and folder proxies | Enron maildir | done B39 (= B09, B10) |
| C10 | Missing-information check: rules vs LLM | Precision and recall of missing items against the gold `mustAsk` | synthetic | done B40 (rules); LLM variant: model |
| C11 | `ExcerptRelevance` | Gold facts kept vs unrelated excerpts removed | synthetic, knowledge | done B36 |
| C12 | `KnowledgeRequirements` | Requirements found in the documents | knowledge | not benchmarked: about 2 requirement sentences in the knowledge; unit tests (B36) |
| C13 | Request extractors: rules vs LLM | Request count exact; over- and under-splitting | synthetic-multi | done B41 (rules); LLM variant: model, harness ready |
| C14 | Conversation history (`ConversationRecorder`, repository) | Own-thread recall after a restart; zero cross-project leaks | synthetic, Enron | done B45 (synthetic); B12 |
| C15 | `AutoTopicMaintainer` | Topic stability across runs, coverage | Enron | new |
| C16 | Routing score (`ScoreRoutingStrategy`) | Accuracy, ambiguity rate | synthetic | B11 |
| C17 | Output guards: echo, placeholder, signature, greeting-only | What the guards removed and what got past them, no model | replies of runs C-D | done B35 |
| C18 | LLM adapter | Latency p50 / p95; failure, timeout and retry rates | every model run | model (logged) |
| C19 | SQLite board (teammate's code) | Revisions, ms per operation, claims with many concurrent boards | synthetic × N | new; agree with the teammate |

## Level 2: parts

| ID | Part | Question and metric | Status |
| --- | --- | --- | --- |
| P1 | MetadataAgent (C01-C07 together) | Macro F1 rules-only vs recommended; latency; on the Enron test set | done B33 (test); B07 on dev |
| P2 | Context and retrieval | Retrieval quality end to end; model input never over budget | re-run; budget check new |
| P3 | Memory | Replies to follow-up emails with vs without stored history | model; new (needs follow-up threads) |
| P4 | Specialists and routing | Confusion matrix over the statement's email types (student, lecturer, administration, project, ambiguous); plans cite only permitted evidence | B11; extend |
| P5 | Blackboard collaboration and control | Router vs blackboard; request coverage; rounds; threshold chosen on the fresh set | done B37 (= B13); fresh set open |
| P6 | Composition in isolation | One recorded board state, different models and prompts, scored with the reply checks: separates the composer from routing | covered: the composer's input is deterministic (B43), so W1 and W3 compare composers on identical input |
| P7 | Review and revision loop | After rejection with feedback: feedback applied, nothing else broken | harness ready, template floor B44; model runs open |
| P8 | Agent service and mailbox | API latency, error paths, approval is not a send | new (functional) |

## Level 3: the whole system

| ID | Benchmark | Variants, one change at a time | Status |
| --- | --- | --- | --- |
| W1 | Reply quality, main result | 3 B and 8 B models, plus a third model swapped by configuration only (to-do 2.3); 3 repeats each | A-D done on development checks (B15-B24); re-run after the reply-check review (to-do 3.10) |
| W2 | Single agent vs multi-agent (statement §20) | One model, one generic prompt, one call vs the blackboard | option `--single-agent` ready; model runs open |
| W3 | Ablations | Without knowledge (3.4, `--knowledge`); neutral composer prompt (3.3, `--composer-prompt`; role prompts reach the model only in the router); router vs blackboard with the model (3.2, `--architecture`); composer prompt variant | options ready; model runs open |
| W4 | Safety | Injection suite through the real composer (3.8); cross-thread disclosure; staff-only document leaks | model |
| W5 | Cost and speed | Model calls, tokens and latency per email, per configuration | from the logs of W1-W4 |
| W6 | Human rubric (3.2) | Two raters on a sample: every request answered, no invented fact, asks for what is missing, tone | human |
| W7 | Error analysis (3.7) | Errors by type with examples: missed request, wrong role, invented fact, disclosure, timeout | derived from W1-W4 |
| W8 | Final run on the fresh held-out set | Best configuration vs baseline, settings frozen before the run | model; last |

## Rules

- One protocol entry per run, invalid runs included, naming the plan ID.
- Settings are fixed before a run and never tuned on the test set or the fresh set.
- Model runs: at least 3 repeats and a paired comparison per email (bootstrap interval or McNemar's test), because one run moves by up to 4 facts of 32 (B25).
- Metadata results on the Enron test set are also given for the 25 hand-labelled emails alone (caveat of B29).
- Every online result names the model and the provider.

## Order

1. **No model:** C03, C05, C06 and P1 on the frozen test set; re-runs of C08, C09 and P5; C17 on saved replies; C07, C11, C12.
2. **Human:** the owner reviews the 36 reply checks (to-do 3.10), which unlocks Level 3. Meanwhile the small harnesses are built (C10, C13, C17, options for W2 and W3, P6, P7).
3. **Online model:** W1 with repeats, then W3, W2, W4, P6, P7, and the LLM variants of C10 and C13.
4. **Last:** the fresh set (written before step 3's results are seen), then W8, the W6 rubric and the W7 error analysis.
