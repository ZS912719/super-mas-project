# Step 1.5: Model Spike (GLiNER and Sentence Encoder Through ONNX)

## Question

Can GLiNER and the SetFit encoder run on this laptop, ideally in-process from Java through ONNX? The plan's fallbacks were a local Python process, then rules only.

## Answer

**GLiNER: yes, in Java, through ONNX, using the half-precision export.** On one test email it returned the same spans as the Python library. The SetFit half of the question was not tested in this step; it is settled in step 1.7.

## What was done

- Ran the Python spike (`scripts/spike/metadata_models_spike.py`) to get a reference output.
- Added `onnxruntime` 1.31.1 and DJL `tokenizers` 0.40.0 to `pom.xml`.
- Wrote `org.supermas.metadata.GlinerEntityModel`, which reproduces the library's prompt format, word mask, span index and greedy decoding.
- Wrote `GlinerSpike` (prints results) and `GlinerEntityModelTest` (parity and chunking).
- Wrote `scripts/spike/onnx_variant_check.py`, which tests any ONNX variant without Java.

## Results

One 161-character synthetic email, on the i5-8265U laptop with 7 GB of RAM:

| Route | File size | Load | Predict | Peak memory (whole process) | Output |
| --- | --- | --- | --- | --- | --- |
| Python, PyTorch | 1.1 GB | 10.0 s | 0.9 s | 2.5 GB (includes the sentence encoder) | Reference |
| Java, `model.onnx` | 1.16 GB | 8.6 s | 0.09-0.17 s | 2.07 GB | Same spans |
| **Java, `model_fp16.onnx`** | **580 MB** | **7.4 s** | **0.24 s** | **1.27 GB** | **Same spans** |
| Python ONNX, `model_int8`, `model_uint8`, `model_quantized` | 349 MB | n/a | n/a | 0.93 GB | **Broken: no entity above 0.14** |

The peak memory includes Maven's JVM, so a stand-alone process would use somewhat less. These timings are for one short email and are not a benchmark.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| In-process ONNX Runtime | Matches the plan's first preference. There is no second process to start, monitor or secure, and 1.27 GB fits on this laptop | A Python process: double the memory and a new failure mode |
| `model_fp16.onnx` | Half the memory of the full model with the same spans on the test email | Full model: about 2.5x faster but 800 MB more memory, and the laptop has about 2 GB free. 8-bit files do not work |
| `onnx-community/gliner_multi-v2.1` | Matches the multilingual model the plan names and is a ready export | Exporting ourselves: the library has no export method in this version, and it would be work with no gain |
| GLiNER code in `org.supermas.metadata` | Entity extraction is part of the `MetadataAgent`, which is ours | Treating it as "LLM integration": that team-owned area concerns the local LLM engine, not small extraction models. **The team should confirm they read it the same way** |
| Tokenize each word separately | Avoids depending on a DJL pre-tokenized API. Gives the same IDs on the checked text, because each word is its own unit | DJL's pre-tokenized encode: less certain behaviour |
| Chunk at 380 tokens, without overlap | The model config has `max_len` 384 | Truncation would silently lose text. Overlap would need duplicate merging; not worth it yet |
| Models in `tmp/models/`, Git-ignored | Hundreds of MB must not enter Git | Git LFS: a team decision with no need yet |

To fetch the model:

```bash
mkdir -p tmp/models/gliner-multi && cd tmp/models/gliner-multi
B=https://huggingface.co/onnx-community/gliner_multi-v2.1/resolve/main
curl -LO $B/tokenizer.json && curl -LO $B/gliner_config.json && curl -LO $B/onnx/model_fp16.onnx
```

## Problems encountered

1. **The first Java run found no entities.** It looked like a bug in my port. Running the same file from Python gave the same empty result, which pointed at the file. The published 8-bit exports are **silently broken**: they load and run but give scores no higher than 0.14. Nothing warns about this. Anyone choosing them for size would get an extractor that finds nothing, and it would look like a weak model.
2. **The GLiNER library has no export function in version 0.2.29,** so I used a third-party export. It is 2 years old and comes from `onnx-community`, not the model's authors.
3. **Python 3.14 warns about `torch.jit`.** It worked here, but this environment is new and unusual. Step 1.7 uses its own virtual environment.
4. **ONNX Runtime prints constant-folding warnings** for the half-precision file. It is cosmetic as far as I can tell, because the output matched.

## Critical points

- **Parity rests on one email.** A broader check against PyTorch on real mail has not been done. The tokenization choice is the most likely source of a difference.
- **No accuracy has been measured.** The spike output already shows real weaknesses: `Prof. Martin` keeps the title, `CS204 project` is called an organization, `Friday 6 November` is a date rather than a deadline, and `student number` is called an identifier. Step 1.6 has to handle these.
- **Load time of about 7 s** is acceptable only if the model stays loaded. The model should be loaded once, not per email.
- **Hard-coded values:** the special token IDs (`CLS` 1, `SEP` 2), maximum width 12 and the chunk size are fixed for this model. A different GLiNER model may need different values, and this is not read from `gliner_config.json`.
- **Chunk boundaries can split an entity** that crosses them, which is a known and accepted loss.
- **Native dependencies:** ONNX Runtime and DJL bring native libraries, which adds about 100 MB or more to the build and may affect the team's packaging. Compatibility beyond this Linux laptop is untested.
- **Licences were not reviewed in depth.** The model is listed under Apache 2.0 upstream, which should be confirmed before any release.
- **French was not tested,** in line with the current English-only decision. The plan notes that the model page does not name French.
- **Tests skip without the model.** A green build on a machine without the model does not exercise this code. This needs to be stated wherever test results are reported.
