# Benchmark Report: entity-rules

| Item | Value |
| --- | --- |
| Dataset | Enron dev set (unreviewed LLM drafts) |
| Dataset SHA-256 | ecccd321e4ffbf114a334627486acb03ce149c7b0d30a7b3440013e2c8a6baee |
| Emails | 100 |
| Predictor failures | 0 |
| Latency p50 | 0.999 ms |
| Latency p95 | 6.633 ms |
| Peak heap | 72 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.20.1 |

## All emails (100 emails)

Macro F1: 0.584

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 857 | 857 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 91 | 71 | 20 | 20 | 0.780 | 0.780 | 0.780 | 1.000 |
| entity:PERSON | 149 | 12 | 3 | 137 | 0.800 | 0.081 | 0.146 | 0.982 |
| entity:COURSE | 2 | 0 | 1 | 2 | 0.000 | 0.000 | 0.000 | 0.990 |
| entity:DATE | 51 | 40 | 19 | 11 | 0.678 | 0.784 | 0.727 | 0.986 |
| entity:DEADLINE | 8 | 5 | 8 | 3 | 0.385 | 0.625 | 0.476 | 0.979 |
| entity:ID | 20 | 6 | 0 | 14 | 1.000 | 0.300 | 0.462 | 1.000 |
| speechActs | 91 | 0 | 0 | 91 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (100 emails)

Macro F1: 0.584

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 857 | 857 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 91 | 71 | 20 | 20 | 0.780 | 0.780 | 0.780 | 1.000 |
| entity:PERSON | 149 | 12 | 3 | 137 | 0.800 | 0.081 | 0.146 | 0.982 |
| entity:COURSE | 2 | 0 | 1 | 2 | 0.000 | 0.000 | 0.000 | 0.990 |
| entity:DATE | 51 | 40 | 19 | 11 | 0.678 | 0.784 | 0.727 | 0.986 |
| entity:DEADLINE | 8 | 5 | 8 | 3 | 0.385 | 0.625 | 0.476 | 0.979 |
| entity:ID | 20 | 6 | 0 | 14 | 1.000 | 0.300 | 0.462 | 1.000 |
| speechActs | 91 | 0 | 0 | 91 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

