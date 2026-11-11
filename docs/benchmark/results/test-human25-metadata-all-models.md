# Benchmark Report: metadata-all-models

| Item | Value |
| --- | --- |
| Dataset | benchmark/data/enron/labeling/frozen-test-human25 |
| Dataset SHA-256 | fd8107a30941a5c10ce4cdbad58408722e57baefc23f10978b14b338c8789682 |
| Emails | 25 |
| Predictor failures | 0 |
| Latency p50 | 325.719 ms |
| Latency p95 | 1568.859 ms |
| Peak heap | 129 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.21.0.3 |

## All emails (25 emails)

Macro F1: 0.709

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 99 | 99 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 24 | 12 | 12 | 12 | 0.500 | 0.500 | 0.500 | 0.000 |
| entity:PERSON | 14 | 14 | 15 | 0 | 0.483 | 1.000 | 0.651 | 0.563 |
| entity:COURSE | 0 | 0 | 3 | 0 | 0.000 | n/a | 0.000 | 0.920 |
| entity:DATE | 31 | 7 | 12 | 24 | 0.368 | 0.226 | 0.280 | 0.889 |
| entity:DEADLINE | 0 | 0 | 1 | 0 | 0.000 | n/a | 0.000 | 0.960 |
| entity:ID | 3 | 1 | 7 | 2 | 0.125 | 0.333 | 0.182 | 0.909 |
| speechActs | 17 | 9 | 12 | 8 | 0.429 | 0.529 | 0.474 | 0.556 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (25 emails)

Macro F1: 0.709

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 99 | 99 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 24 | 12 | 12 | 12 | 0.500 | 0.500 | 0.500 | 0.000 |
| entity:PERSON | 14 | 14 | 15 | 0 | 0.483 | 1.000 | 0.651 | 0.563 |
| entity:COURSE | 0 | 0 | 3 | 0 | 0.000 | n/a | 0.000 | 0.920 |
| entity:DATE | 31 | 7 | 12 | 24 | 0.368 | 0.226 | 0.280 | 0.889 |
| entity:DEADLINE | 0 | 0 | 1 | 0 | 0.000 | n/a | 0.000 | 0.960 |
| entity:ID | 3 | 1 | 7 | 2 | 0.125 | 0.333 | 0.182 | 0.909 |
| speechActs | 17 | 9 | 12 | 8 | 0.429 | 0.529 | 0.474 | 0.556 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

