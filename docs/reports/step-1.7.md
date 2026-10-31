# Step 1.7: Offline Speech-Act Classifier and ONNX Export

## Outcome

**A speech-act classifier is exported to ONNX, but it is the head-only baseline, not SetFit.** The roadmap asked for SetFit. SetFit did not beat the simpler model, so the rule agreed before the results were known selected head-only. Quality is low on both models, and this step does not show that either one is useful.

The step also had to fix training that was repeatedly killed for lack of memory. Training now finishes on the 7.1 GB laptop while IntelliJ is open.

## What was built

| File | Role |
| --- | --- |
| `scripts/training/train_setfit.py` | `cv` compares the models with 5-fold cross-validation. `export` trains one on all dev data and writes it for Java |
| `tmp/models/speech-act/` (Git-ignored) | `model.onnx` (470 MB), tokenizer, `head.json` (four linear classifiers) and `version.json` |
| `tmp/training/cv.json` (Git-ignored) | Full cross-validation results, reproduced below |

The model encodes the email's `currentText` with `paraphrase-multilingual-MiniLM-L12-v2`, up to 128 tokens, using mean pooling. One logistic regression per act then makes a yes or no decision, and an email can receive several acts or none.

## Results

Enron dev set: 100 emails, of which 91 have current text to classify. **All labels are my unreviewed drafts, so these scores are provisional.** Exact counts in the table are out-of-fold, summed across the five folds.

| Act (positives in 91) | Head-only F1 | Head-only TP / FP / FN | SetFit F1 | SetFit TP / FP / FN |
| --- | --- | --- | --- | --- |
| deliver (47) | 0.633 | 31 / 20 / 16 | 0.617 | 29 / 18 / 18 |
| request (28) | 0.406 | 13 / 23 / 15 | 0.300 | 9 / 23 / 19 |
| commit (10) | 0.000 | 0 / 5 / 10 | 0.111 | 1 / 7 / 9 |
| propose (6) | 0.000 | 0 / 1 / 6 | 0.000 | 0 / 1 / 6 |
| **Macro F1** | **0.260** | | **0.257** | |

| Cost | Head-only | SetFit (batch 8) |
| --- | --- | --- |
| Peak memory per fold, Python process | 1,116 MB | 2,572 MB |
| Total time for five folds | 66 s | 1,467 s (about 24 min) |

The export finished with a peak of 2,687 MB. The ONNX model plus head matched the Python model's decisions on **91 of 91** emails.

## The memory problem

The kernel log shows `python` killed by the system-wide out-of-memory killer at 15:35 (3.9 GB in RAM) and 17:17 (5.4 GB), with swap full both times. IntelliJ was using about 2.9 GB on the same machine.

| Run | Result |
| --- | --- |
| Before any fix, batch 16, one process | Killed with the whole machine out of memory |
| Word embeddings frozen, batch 16, 3.5 GB cap | Killed by the cap at about 3.6 GB, with the rest of the machine unaffected |
| Word embeddings frozen, batch 8, 3.5 GB cap | Finished, peak about 2.56 GB per fold |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| **Export head-only** | SetFit needed to score higher and did not. The 0.003 gap is noise on 91 emails, so cost settles it: head-only uses less than half the memory and is about 22 times faster to train | Shipping SetFit because the plan named it: more cost with no measured gain |
| Selection rule fixed before seeing results | Avoids choosing the favourite afterwards | Deciding after seeing the scores |
| Freeze the word embeddings during SetFit | They hold 82% of the weights, so they should dominate gradient and optimizer memory. Few emails can usefully retrain them | Training the full model: never completed on this machine |
| Batch size 8 | The agreed fallback once batch 16 hit the cap | Shorter texts: would change what the model sees. Fewer pairs: would weaken training |
| One child process per model and fold | All memory is guaranteed back when each ends | One process with explicit clean-up: depends on no references surviving |
| Save each fold, and reuse it only if data and settings match | A crash loses one fold, and stale results cannot be mixed in | Restarting after a crash: wastes about 25 minutes per attempt |
| Run under `MemoryMax=3.5G`, `MemorySwapMax=0` | Overshooting ends this job, not the desktop | No cap: the kernel chooses the victim, which was IntelliJ at 14:45 |
| Same ONNX and `head.json` form for both models | Step 1.8 needs only one loader, and SetFit can replace head-only with `--model setfit` | A separate format per model |
| `--model` defaults to `headOnly` | Matches the current decision | Always training SetFit in `export` |

## Problems encountered

1. **Freezing alone was not enough.** I expected it to fit at batch 16. It did not, so the plan's fallback was used.
2. **Export needed code that the plan had only implied.** It always trained SetFit, so `--model` was added.
3. **The output is noisy with warnings.** The ONNX exporter reports that it is a legacy exporter and that some values are fixed while tracing. Agreement across emails of different lengths suggests the padding warning does no harm here, but the legacy exporter may be removed from a later PyTorch release.
4. **The Hugging Face Hub was bypassed.** Runs used `HF_HUB_OFFLINE=1` and the cached model, so the base model is identified by its name and cache, and the script does not pin a revision. The cached snapshot was `e8f8c211`.

## Critical points

- **Neither model learned `propose` or `commit`.** There are only 6 and 10 positive examples. The two zeros in head-only mean those acts are never usefully found, and they pull macro F1 down.
- **The 1.10 gate may fail.** It requires models to beat rules on labels. At this quality that is uncertain, and I have not yet compared against the rules.
- **The labels are unreviewed.** The scores measure agreement with my drafts. A wrong draft both trains and grades the model. The model is marked `provisional` and should be retrained after review.
- **The verdict is limited to this setup.** SetFit was tested only with frozen embeddings, batch 8 and one epoch on 100 draft-labelled emails, on one seed. This does not show that SetFit is worse in general. It could win with more data, and `--model setfit` keeps that test available.
- **The comparison is the same data used for selection.** No separate data was held back, because the frozen test set stays untouched until the end.
- **What was not measured:**
  - The 1.9 GB and 0.73 GB training memory figures from the plan are calculations, not measurements, and unfrozen training was never run to compare.
  - I did not separately check that the frozen weights stayed unchanged during training. The script sets the flag, and the trainer is expected to respect it.
  - Accumulation across folds in the old script was a suspicion that I did not confirm. The per-process design removes the risk either way.
- **The export check is narrow.** It runs one email at a time, on the training emails. Batched input is untested. It checks only that the export is faithful, not that the model is correct.
- **Java has not loaded this model yet.** Step 1.5 left the SetFit half of its Java question to this step, and that part is still open. Step 1.8 must load it and reproduce these decisions, including tokenization with the `tokenizer.json` written here.
- **Runtime memory will grow.** Weights alone are 470 MB. Together with GLiNER's roughly 1 GB of native memory, this has to be measured in step 1.9 on a machine with little to spare.
- **Multilingual behaviour is untested.** The encoder supports other languages, but this data is English. Nothing here supports a claim about French quality.
- **Only the first 128 tokens count.** A request at the end of a long email is not seen.
- **Prompt injection:** email text is only classified, but a crafted email can still change the predicted acts. The labels are hints and must not grant permissions or trigger actions.
- **Reproducibility:** `tmp/` is Git-ignored, so the exported model and `cv.json` are not committed. They must be regenerated with the commands in the script, and the seed is fixed. The environment is the unusually new Python 3.14 noted in step 1.5.
- **The script's name is now slightly misleading.** `train_setfit.py` also trains and exports head-only. I kept the name because SetFit remains an option and renaming adds nothing for now.
