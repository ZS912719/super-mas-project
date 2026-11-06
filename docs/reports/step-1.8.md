# Step 1.8: Label Classification

## Outcome

**Java now classifies speech acts two ways: phrase rules and the exported ONNX model. On the provisional labels, the rules score about twice as high as the model (macro F1 0.540 vs 0.260).** That result is biased in favour of the rules, as explained below, so it does not yet settle which is better.

## What was built

| Class | Role |
| --- | --- |
| `metadata/SpeechAct` | One predicted act with its confidence and source |
| `metadata/SpeechActClassifier` | Common interface |
| `metadata/RuleSpeechActClassifier` | English phrase rules for the four acts |
| `metadata/OnnxSpeechActClassifier` | Tokenizer, ONNX encoder and four linear classifiers, in-process |
| `metadata/RuleRoleHintClassifier` | Role hint from what the sender says about themselves |
| `benchmark/LabelPredictor` | Benchmark variants `labels-rules` and `labels-model` |
| `benchmark/RuleLabelComparison` | Scores rules on the exact text used in cross-validation. Prints counts only |
| `train_setfit.py parity` | Writes Python's output, so Java can be compared with it |

`jackson-databind` moved from test scope to compile scope, because the production classifier must read `head.json`.

## Results

Enron dev set. **All labels are unreviewed LLM drafts.** The first table uses the 91 emails and the same text as the step 1.7 cross-validation, so its columns compare directly.

| Act (positives) | Rules F1 | Rules TP / FP / FN | Head-only F1 (out of fold) | SetFit F1 (out of fold) |
| --- | --- | --- | --- | --- |
| request (28) | 0.787 | 24 / 9 / 4 | 0.406 | 0.300 |
| propose (6) | 0.286 | 1 / 0 / 5 | 0.000 | 0.000 |
| commit (10) | 0.529 | 9 / 15 / 1 | 0.000 | 0.111 |
| deliver (47) | 0.559 | 19 / 2 / 28 | 0.633 | 0.617 |
| **Macro F1** | **0.540** | | 0.260 | 0.257 |

Rules ran on the labelled text here. In the production path they run on the stripper's output: `dev-provisional-labels-rules.md` records precision 0.663, recall 0.582 and F1 0.620 pooled over all acts on 100 emails.

| Measure | Rules | ONNX model |
| --- | --- | --- |
| Latency p50 / p95, including parsing | 1 ms / 8 ms | 24 ms / 73 ms |
| Peak Java heap, excluding native memory | 72 MB | 185 MB |

**Java parity:** on all 91 emails, Java made the same decisions as Python, and each embedding value differed by less than 0.0001. This covers tokenization, encoding and classification. It settles the question left open in step 1.5: this kind of model runs in Java through ONNX.

**Role hint:** F1 0.500 from one correct hint out of 3 gold values, which is too few to mean anything.

**The whole suite:** 52 tests, 0 failures, 0 skipped.

## Why the model was not scored on dev in Java

The exported model was trained on these 100 emails. Scored on them from Java it gets F1 0.995 for speech acts, against 0.26 macro F1 out of fold. That in-sample number is worthless as a quality measure, so it is not in `docs/benchmark/results`. The gap also shows heavy overfitting. Only the cross-validated numbers are used for comparison.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Write the rules without reading dev emails | Avoids tuning rules to the data they are compared on | Tuning them on dev: would inflate the comparison further |
| Evaluate rules on the cross-validation text | The model comparison must use the same input | Comparing different text sets: differences would be uninterpretable |
| Parity test over all 91 emails | The tokenizer is a likely place for Java and Python to differ, and the check covers real text | One example sentence: weak |
| One classifier interface | Step 1.9 can switch stages without branching on type | Two unrelated classes |
| Skip the model for blank text | It was trained only on emails with new text, and blank input wastes a run | Sending empty text: output has no meaning |
| Sigmoid of the linear score as confidence | Matches logistic regression, and the decision stays `score > 0` | Hard yes or no: loses information |
| Rule confidence always 1.0 | A rule only says a phrase was present. Any other number would be invented | A made-up 0.7 |
| Jackson in compile scope | It is already used in the project and parses `head.json` correctly | A hand-written parser: more risk |
| Role hint as rules only | 3 gold values cannot train or evaluate a model | Training a classifier on them |

## Problems encountered

1. **The benchmark loader rejected the dev folder.** It requires `.gold.json`, and labels here are drafts with extra `_` fields. As in step 1.6, I used a Git-ignored copy with those fields removed, in `tmp/training/dev-provisional`.
2. **The result surprised me.** A simple phrase list doubling the model's score looked suspicious, so I checked how the labels were made. That led to the bias below.

## Critical points

- **The comparison is biased towards rules.** Each draft act had to be backed by a quote found in the email (`labeling-setup.md`). That favours acts stated in explicit phrases, which is exactly what rules detect. The same LLM also wrote the schema, the labels and these rules, and the schema's own examples guided the phrases. Not reading dev emails does not remove this.
- **The hand-labelled audit decides this.** The 25 audit emails are the only labels independent of me. Until they exist, "rules beat the model" is a provisional finding, not a conclusion.
- **Models are also disadvantaged.** They trained on only about 73 emails per fold, so a larger labelled set could change the result.
- **Rule weaknesses:** `commit` has 15 false positives because `I will` and `we will` also occur in plain statements. `deliver` misses 28 of 47, because delivered information often has no fixed phrase. `please` alone counts as a request, and `propose` found just 1 of 6.
- **The rules are English-only.** French needs its own phrases when French data returns.
- **Model memory:** Java heap excludes ONNX native memory, which was not measured here. Step 1.10 measures the combined load.
- **Training vs production text.** The model trained on labelled `currentText`, but production feeds it the stripper's output. The two are close on dev, but the difference is not measured for the model out of fold.
- **Prompt injection:** a crafted email can change acts and role hints in both classifiers. They are hints, and the role hint in particular must never grant permissions.
- **Hard-coded path:** the benchmark loads `tmp/models/speech-act`. Tests skip if it is missing, which hides this test on machines without the export. The export and parity commands regenerate it.
