# Benchmark Report: labels-rules

| Item | Value |
| --- | --- |
| Dataset | Enron dev set (unreviewed LLM drafts) |
| Dataset SHA-256 | ecccd321e4ffbf114a334627486acb03ce149c7b0d30a7b3440013e2c8a6baee |
| Emails | 100 |
| Predictor failures | 0 |
| Latency p50 | 1.262 ms |
| Latency p95 | 7.883 ms |
| Peak heap | 72 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.20.1 |

## All emails (100 emails)

Macro F1: 0.531

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 857 | 857 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 91 | 71 | 20 | 20 | 0.780 | 0.780 | 0.780 | 1.000 |
| entity:PERSON | 149 | 0 | 0 | 149 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 2 | 0 | 0 | 2 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DATE | 51 | 0 | 0 | 51 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 8 | 0 | 0 | 8 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 20 | 0 | 0 | 20 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 91 | 53 | 27 | 38 | 0.663 | 0.582 | 0.620 | 0.815 |
| roleHint | 3 | 1 | 0 | 2 | 1.000 | 0.333 | 0.500 | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (100 emails)

Macro F1: 0.531

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 857 | 857 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 91 | 71 | 20 | 20 | 0.780 | 0.780 | 0.780 | 1.000 |
| entity:PERSON | 149 | 0 | 0 | 149 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 2 | 0 | 0 | 2 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DATE | 51 | 0 | 0 | 51 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 8 | 0 | 0 | 8 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 20 | 0 | 0 | 20 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 91 | 53 | 27 | 38 | 0.663 | 0.582 | 0.620 | 0.815 |
| roleHint | 3 | 1 | 0 | 2 | 1.000 | 0.333 | 0.500 | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

