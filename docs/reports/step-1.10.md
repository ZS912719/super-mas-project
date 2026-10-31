# Step 1.10: Phase 1 Benchmark and Gate

## Outcome

**The Phase 1 gate is not fully met.** On provisional labels:

| Gate condition | Result |
| --- | --- |
| Facts right without a model | **Mostly.** Sender, recipients, date and language are perfect, but `currentText` is only 0.780, and threading is untested on this sample |
| Models beat rules on entities | **Yes** for PERSON, COURSE and ID. DATE and DEADLINE use rules by design |
| Models beat rules on labels | **No.** Rules reach macro F1 0.540 and the models about 0.26 |

No label here has been checked by a person, so the audit must happen before this is a final result.

**Current decision:** the recommended configuration is GLiNER for entities plus rules for speech acts (`metadata-entity-model`). The speech-act model stays available behind its switch.

## Results

Enron dev set, 100 English emails. **All labels are unreviewed LLM drafts.** Reports are in `docs/benchmark/results/dev-provisional-metadata-*.md`. French is deferred, so there is one language to report.

| Field (gold count) | Rules only F1 | Recommended F1 |
| --- | --- | --- |
| sender (100), date (100), language (100) | 1.000 | 1.000 |
| recipients (857) | 1.000 | 1.000 |
| threadId (100) | 1.000, trivial | 1.000, trivial |
| currentText (91) | 0.780 | 0.780 |
| PERSON (149) | 0.146 | **0.826** |
| COURSE (2) | 0.000 | **1.000** |
| DATE (51) | 0.727 | 0.727 |
| DEADLINE (8) | 0.476 | 0.476 |
| ID (20) | 0.462 | **0.688** |
| speechActs (91, pooled) | 0.620 | 0.620 |
| roleHint (3) | 0.500 | 0.500 |
| **Macro F1, as computed by the runner** | 0.670 | 0.817 |

Speech acts by model type, on the same 91 emails and text. Model figures are out of fold, and details are in step 1.8.

| Macro F1 over the four acts | Rules | Head-only | SetFit |
| --- | --- | --- | --- |
| | **0.540** | 0.260 | 0.257 |

| Cost | Rules only | Recommended | Both models on |
| --- | --- | --- | --- |
| Latency p50 | 2 ms | 312 ms | 350 ms |
| Latency p95 | 8 ms | 2,282 ms | 2,311 ms |
| Peak process memory, including Maven's JVM | 128 MB | 1,757 MB | 2,262 MB |

Model loading time is not included. The speech-act model adds about 500 MB and gives no measured quality gain, which is another reason to keep it off.

`metadata-all-models` also ran, but its speech-act score of 0.995 is in-sample, so its report is not published.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Default to rules for speech acts | Higher score on current evidence, 1 ms instead of 24 ms, about 500 MB less memory | Following the original plan's model default against the measurements |
| Keep the model switch | The audit could reverse the finding | Deleting the model |
| Do not call the gate passed | Rules beat the models, and the labels are unverified | Rewriting the gate after seeing the result |
| Do not touch the frozen test set | The audit is unfinished, and `labeling-setup.md` forbids it | Running on it for more numbers |
| Measure peak memory with a cgroup | Includes native ONNX memory, which the Java heap figure misses | Heap only: understates memory by over 1 GB |

## Problems encountered

- `currentText` at 0.780 was already known from step 1.3b. It uses strict exact matching, so one extra line makes a full miss. Part of this may be label boundaries, and part real stripper error.
- The memory figures include Maven's own JVM, because the benchmark runs inside it. Actual production use should be somewhat lower, but I have not measured by how much.

## Critical points

- **The human audit is the deciding step.** It is the only source independent of the LLM that wrote the schema, labels and rules. See `labeling-setup.md`. Its agreement figures must accompany any accuracy result.
- **The labels favour rules.** They needed a supporting quote, which favours explicit phrases (step 1.8).
- **Selection and reporting use one set.** Rules were not tuned on dev, but the entity hybrid and threshold were. Absolute numbers will probably drop on the test set.
- **Threading is untested on real data here.** The sample holds only single-email threads, so 1.000 means nothing. Synthetic threading did pass in step 1.2.
- **Rare classes:** COURSE (2), DEADLINE (8), propose (6), commit (10) and roleHint (3) are too small for reliable scores. Enron also lacks university mail, the project's real domain, so COURSE and `student` remain largely unmeasured.
- **Per-language comparison is incomplete.** The plan asks for it per language, and French awaits data.
- **Memory:** about 1.8 GB for the recommended setup leaves little room on this laptop, particularly with an IDE running. It should be reconsidered once the blackboard, retrieval and LLM share the machine.
- **Macro F1 of 0.817 vs 0.670 is not a quality headline.** It averages easy fact fields with entity fields, and the rules-only figure counts fields the same way. Use the per-field numbers.
