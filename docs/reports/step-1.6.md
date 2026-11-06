# Step 1.6: Entity Extraction

## What was built

| Class | Role |
| --- | --- |
| `metadata/EntityMention` | One extracted entity with its type, text, confidence and source |
| `metadata/RuleEntityExtractor` | Regular expressions for dates, deadlines, course codes, identifiers and people named in greetings |
| `metadata/GlinerEntityExtractor` | Maps GLiNER spans to schema types and applies annotation rules such as removing titles |
| `benchmark/EntityPredictor` | Benchmark variants: `entity-rules`, `entity-gliner`, `entity-gliner-rules`, `entity-hybrid` |
| `RuleEntityExtractorTest` | Unit tests for the rules |

`BenchmarkRunner` now creates predictors lazily, so running a rules-only variant does not load a 580 MB model.

## Results

Enron dev set, 100 emails. **These are provisional: the labels are my unreviewed drafts, and I chose the design using this same data.** Reports are in `docs/benchmark/results/dev-provisional-*.md`.

| Entity (gold count) | Rules F1 | GLiNER F1 | Rules + GLiNER, all types | **Hybrid F1** |
| --- | --- | --- | --- | --- |
| PERSON (149) | 0.146 | 0.826 | 0.818 | **0.826** |
| COURSE (2) | 0.000 | 1.000 | 0.800 | **1.000** |
| DATE (51) | 0.727 | 0.580 | 0.692 | **0.727** |
| DEADLINE (8) | 0.476 | 0.533 | 0.500 | **0.476** |
| ID (20) | 0.462 | 0.385 | 0.688 | **0.688** |

| Variant | Latency p50 | Latency p95 | Peak Java heap |
| --- | --- | --- | --- |
| Rules | 1 ms | 7 ms | 72 MB |
| Hybrid | 261 ms | 1,914 ms | 210 MB, plus about 1 GB of native memory |

Lowering the GLiNER threshold from 0.5 to 0.3 made every type worse or no better on F1, so 0.5 stays.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| **Hybrid: GLiNER for PERSON and COURSE, rules for DATE and DEADLINE, both for ID** | Each source won on those types on dev. Dates have regular forms, while names do not | Union of everything: more false positives. GLiNER alone: worse dates and IDs |
| Model decides first, then rules add | When both find the same text, one type is kept and the model's wins | Rules first: the model's context would be ignored |
| The words before a date decide DATE vs DEADLINE | GLiNER rarely separates these labels, and the difference is usually a nearby cue | Trusting the model's `deadline` label: it missed "Friday 6 November" in the spike |
| "organization" requested and then discarded | Without a label for them, company names are likely to be forced into another type | Removing it: not measured, so this is an assumption |
| Threshold 0.5 | Best or tied on dev | 0.3: more false positives and almost no gain in recall |
| Extract from `currentText` only | The schema says quoted text and signatures do not count | The whole body: would pick up every name in a forward chain |
| Keep `confidence` and `source` on each mention | The plan's P1 needs them, and provenance is a project invariant | Bare strings |
| A separate `EntityMention` type, not `Domain` | Shared records need team validation | Adding to `Domain.java` now. Step 1.9 maps these into `Analysis` under P1 |
| Rules as the floor, even where they lose | The plan requires a rules-only variant, and it works if a model file is absent | Removing them |

## Problems encountered

1. **New tests caught two rule bugs:** dates kept a trailing full stop (`Friday 6 November.`), and "number is 20231187" was missed. Both are fixed, and neither moved the dev scores, so the benchmark alone would not have revealed them.
2. **Entity exact match punishes small differences.** `Tuesday, December 12` and `December 12` count as a full miss and a false positive.
3. **DEADLINE is unreliable.** Only 8 gold mentions exist, and the cue words (`by`, `until`, `through`) also occur in non-deadline contexts.

## Critical points

- **Labels and system share an author's view.** I wrote the schema rules, the labels and these extraction rules. GLiNER is independent of that, but the rules are not. Their scores are likely to be inflated.
- **The hybrid was selected on the data it is reported on.** Expect lower scores on the test set. The comparison between variants is the useful result here, not the absolute numbers.
- **PERSON precision is only 0.77,** with 40 false positives. Many gold labels use the fullest form of a name, which the model may not. The labelling choice and the scoring rule both contribute.
- **COURSE is effectively untested.** Two mentions in a corporate set cannot justify a score of 1.000, and university mail is where it matters.
- **Rule dates do not exclude quantities or versions,** such as a `6/10` that is not a date. Bare weekdays are also common false positives.
- **Latency:** p95 of 1.9 s is for long emails with several chunks. It is fine for an email assistant, but it does not include model loading (about 7 s), so the model must stay loaded.
- **Memory:** the numbers above exclude Maven's overhead, and the laptop has only about 2 GB free. Running other heavy models at the same time, such as the SetFit encoder, needs measuring in step 1.9.
- **Rule constants are duplicated:** the deadline cue exists in both extractors with slightly different lists. This is a small flaw, left because the GLiNER extractor's date output is unused in the hybrid.
- **Prompt injection:** email text reaches GLiNER as data to tag. It cannot change instructions, because the model has none, but a crafted email can still change which entities are found. Entities are hints and must never grant permissions.
- **The entity labels and rules are English-centred.** French dates such as `6 novembre` are not matched by these rules. This is an accepted gap under the English-only decision and must be closed when French returns.
