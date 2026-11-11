# Request Extraction (benchmark plan C13)

Run 2026-11-11T11:30Z on the 12 multi-request synthetic emails (gold request counts of `multi-gold/v1`; 23 requests). Model: template (no model).

| Extractor | Count exact | Too few | Too many | Requests found / gold | Fell back to rules |
| --- | --- | --- | --- | --- | --- |
| rules | 11 / 12 | 1 | 0 | 22 / 23 | — |

| Email | Gold | rules |
| --- | --- | --- |
| en-m01 | 2 | 2 |
| en-m02 | 2 | 2 |
| en-m03 | 2 | 2 |
| en-m04 | 2 | 2 |
| en-m05 | 2 | 2 |
| en-m06 | 1 | 1 |
| en-m07 | 2 | 1 |
| en-m08 | 2 | 2 |
| en-m09 | 2 | 2 |
| en-m10 | 1 | 1 |
| en-m11 | 3 | 3 |
| en-m12 | 2 | 2 |
