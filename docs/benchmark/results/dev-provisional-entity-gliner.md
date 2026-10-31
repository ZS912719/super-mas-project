# Benchmark Report: entity-gliner

| Item | Value |
| --- | --- |
| Dataset | Enron dev set (unreviewed LLM drafts) |
| Dataset SHA-256 | ecccd321e4ffbf114a334627486acb03ce149c7b0d30a7b3440013e2c8a6baee |
| Emails | 100 |
| Predictor failures | 0 |
| Latency p50 | 248.679 ms |
| Latency p95 | 1870.138 ms |
| Peak heap | 210 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.20.1 |

## All emails (100 emails)

Macro F1: 0.700

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 857 | 857 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 91 | 71 | 20 | 20 | 0.780 | 0.780 | 0.780 | 1.000 |
| entity:PERSON | 149 | 133 | 40 | 16 | 0.769 | 0.893 | 0.826 | 0.679 |
| entity:COURSE | 2 | 2 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| entity:DATE | 51 | 29 | 20 | 22 | 0.592 | 0.569 | 0.580 | 0.959 |
| entity:DEADLINE | 8 | 4 | 3 | 4 | 0.571 | 0.500 | 0.533 | 0.990 |
| entity:ID | 20 | 5 | 1 | 15 | 0.833 | 0.250 | 0.385 | 0.989 |
| speechActs | 91 | 0 | 0 | 91 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (100 emails)

Macro F1: 0.700

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 857 | 857 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 91 | 71 | 20 | 20 | 0.780 | 0.780 | 0.780 | 1.000 |
| entity:PERSON | 149 | 133 | 40 | 16 | 0.769 | 0.893 | 0.826 | 0.679 |
| entity:COURSE | 2 | 2 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| entity:DATE | 51 | 29 | 20 | 22 | 0.592 | 0.569 | 0.580 | 0.959 |
| entity:DEADLINE | 8 | 4 | 3 | 4 | 0.571 | 0.500 | 0.533 | 0.990 |
| entity:ID | 20 | 5 | 1 | 15 | 0.833 | 0.250 | 0.385 | 0.989 |
| speechActs | 91 | 0 | 0 | 91 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

