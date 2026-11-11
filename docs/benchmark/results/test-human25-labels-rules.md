# Benchmark Report: labels-rules

| Item | Value |
| --- | --- |
| Dataset | benchmark/data/enron/labeling/frozen-test-human25 |
| Dataset SHA-256 | fd8107a30941a5c10ce4cdbad58408722e57baefc23f10978b14b338c8789682 |
| Emails | 25 |
| Predictor failures | 0 |
| Latency p50 | 1.727 ms |
| Latency p95 | 8.395 ms |
| Peak heap | 27 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.21.0.3 |

## All emails (25 emails)

Macro F1: 0.579

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 99 | 99 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 24 | 12 | 12 | 12 | 0.500 | 0.500 | 0.500 | 0.000 |
| entity:PERSON | 14 | 0 | 0 | 14 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| entity:DATE | 31 | 0 | 0 | 31 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| entity:ID | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 17 | 6 | 18 | 11 | 0.250 | 0.353 | 0.293 | 0.444 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (25 emails)

Macro F1: 0.579

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 99 | 99 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 24 | 12 | 12 | 12 | 0.500 | 0.500 | 0.500 | 0.000 |
| entity:PERSON | 14 | 0 | 0 | 14 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| entity:DATE | 31 | 0 | 0 | 31 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| entity:ID | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 17 | 6 | 18 | 11 | 0.250 | 0.353 | 0.293 | 0.444 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 25 | 25 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

