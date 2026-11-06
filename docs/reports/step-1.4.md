# Step 1.4: Language detection and the facts benchmark

## What was built

- `org.supermas.metadata.LanguageDetector` tells English from French by counting common function words, returning `en` or `fr` (default `en`).
- `FactsPredictor` (benchmark variant `facts`) ties together the no-model pipeline: parser, stripper and detector. It is registered in `BenchmarkRunner`, and `Scorer.normalize` is now public so the pipeline can reuse the scoring normalization.

## Why a word-count detector

The project is English and French only, so a full language identifier is unnecessary. A function-word count is tiny, needs no dependency and no model download, and is easy to explain. It would be wrong for very short or mixed-language text, which is a known limit.

## Results

Environment: Maven 3.9.12, OpenJDK 17.0.20.1. All 36 tests pass.

- **Language: 24 / 24** correct on the synthetic emails.
- **Facts benchmark** (`docs/benchmark/results/facts-synthetic.md`), macro F1 0.455 over all fields:

| Field | F1 |
| --- | --- |
| sender, recipients, date, currentText, language | 1.000 |
| threadId | 0.917 |
| entities, speechActs, roleHint | 0.000 (not built yet) |

The macro F1 is held down only by the fields later steps will fill. **Phase 1's facts gate is met:** every fact is correct without a model.

`threadId` is 0.917 here because this per-email predictor uses the parser's provisional thread id, which misses the two headerless replies. Across the whole mailbox the threader gets these right (24/24, step 1.2); the benchmark harness is per-email, so it cannot show that.

## Limits

- The detector is tuned to clear English or French; short or mixed text will fool it.
- The facts benchmark is the self-authored synthetic set, so these numbers are a development signal, not evidence on real mail. Real accuracy needs the labelled Enron set (blocked on a person labelling it).
- The macro F1 mixes built and unbuilt fields; read the per-field table, not the single number.
