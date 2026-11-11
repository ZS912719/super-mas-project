# Request Extraction (benchmark plan C13)

Run 2026-11-11T12:11Z on the 12 multi-request synthetic emails (gold request counts of `multi-gold/v1`; 23 requests). Model: ONLINE model meta-llama/llama-3.2-3b-instruct at https://openrouter.ai/api/v1 (development stand-in: email text is sent to openrouter.ai), recording replies to runtime/llm-runs/c13-3b.

| Extractor | Count exact | Too few | Too many | Requests found / gold | Fell back to rules |
| --- | --- | --- | --- | --- | --- |
| rules | 11 / 12 | 1 | 0 | 22 / 23 | — |
| llm r1 | 12 / 12 | 0 | 0 | 23 / 23 | 5 |
| llm r2 | 12 / 12 | 0 | 0 | 23 / 23 | 5 |
| llm r3 | 12 / 12 | 0 | 0 | 23 / 23 | 4 |

| Email | Gold | rules | llm r1 | llm r2 | llm r3 |
| --- | --- | --- | --- | --- | --- |
| en-m01 | 2 | 2 | 2 | 2 | 2 |
| en-m02 | 2 | 2 | 2 | 2 | 2 |
| en-m03 | 2 | 2 | 2 | 2 | 2 |
| en-m04 | 2 | 2 | 2 | 2 | 2 |
| en-m05 | 2 | 2 | 2 | 2 | 2 |
| en-m06 | 1 | 1 | 1 | 1 | 1 |
| en-m07 | 2 | 1 | 2 | 2 | 2 |
| en-m08 | 2 | 2 | 2 | 2 | 2 |
| en-m09 | 2 | 2 | 2 | 2 | 2 |
| en-m10 | 1 | 1 | 1 | 1 | 1 |
| en-m11 | 3 | 3 | 3 | 3 | 3 |
| en-m12 | 2 | 2 | 2 | 2 | 2 |
