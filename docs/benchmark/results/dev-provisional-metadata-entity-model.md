# Benchmark Report: metadata-entity-model

| Item | Value |
| --- | --- |
| Dataset | Enron dev set (unreviewed LLM drafts) |
| Dataset SHA-256 | ecccd321e4ffbf114a334627486acb03ce149c7b0d30a7b3440013e2c8a6baee |
| Emails | 100 |
| Predictor failures | 0 |
| Latency p50 | 311.601 ms |
| Latency p95 | 2281.936 ms |
| Peak heap | 190 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.20.1 |

## All emails (100 emails)

Macro F1: 0.817

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 857 | 857 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 91 | 71 | 20 | 20 | 0.780 | 0.780 | 0.780 | 1.000 |
| entity:PERSON | 149 | 133 | 40 | 16 | 0.769 | 0.893 | 0.826 | 0.679 |
| entity:COURSE | 2 | 2 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| entity:DATE | 51 | 40 | 19 | 11 | 0.678 | 0.784 | 0.727 | 0.986 |
| entity:DEADLINE | 8 | 5 | 8 | 3 | 0.385 | 0.625 | 0.476 | 0.979 |
| entity:ID | 20 | 11 | 1 | 9 | 0.917 | 0.550 | 0.688 | 0.989 |
| speechActs | 91 | 53 | 27 | 38 | 0.663 | 0.582 | 0.620 | 0.815 |
| roleHint | 3 | 1 | 0 | 2 | 1.000 | 0.333 | 0.500 | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (100 emails)

Macro F1: 0.817

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 857 | 857 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 91 | 71 | 20 | 20 | 0.780 | 0.780 | 0.780 | 1.000 |
| entity:PERSON | 149 | 133 | 40 | 16 | 0.769 | 0.893 | 0.826 | 0.679 |
| entity:COURSE | 2 | 2 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| entity:DATE | 51 | 40 | 19 | 11 | 0.678 | 0.784 | 0.727 | 0.986 |
| entity:DEADLINE | 8 | 5 | 8 | 3 | 0.385 | 0.625 | 0.476 | 0.979 |
| entity:ID | 20 | 11 | 1 | 9 | 0.917 | 0.550 | 0.688 | 0.989 |
| speechActs | 91 | 53 | 27 | 38 | 0.663 | 0.582 | 0.620 | 0.815 |
| roleHint | 3 | 1 | 0 | 2 | 1.000 | 0.333 | 0.500 | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

