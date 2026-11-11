# Parser Robustness on Enron (benchmark plan C01)

Run 2026-11-11T11:43Z. Every file of the Enron maildir (517401 messages) through `MimeEmailParser` (permissive), `QuoteSignatureStripper` and `LanguageDetector`. Counts only.

| Measure | Messages | Share |
| --- | --- | --- |
| empty body | 0 | 0.0000 |
| empty current text after stripping | 55564 | 0.1074 |
| no Message-ID (synthetic id used) | 0 | 0.0000 |
| no date | 0 | 0.0000 |
| no recipients | 20382 | 0.0394 |
| no sender address | 0 | 0.0000 |
| parsed | 517401 | 1.0000 |
| thread id differs from message id (a reply) | 0 | 0.0000 |

| Detected language of the current text | Messages |
| --- | --- |
| en | 517170 |
| fr | 231 |

| Time per message | p50 | p95 | p99 |
| --- | --- | --- | --- |
| Parse | 0.036 ms | 0.109 ms | 0.299 ms |
| Parse + strip + language | 0.087 ms | 0.473 ms | 1.543 ms |

Wall time 183 s, including reading the files.
