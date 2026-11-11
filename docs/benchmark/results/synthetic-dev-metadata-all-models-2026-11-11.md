# Benchmark Report: metadata-all-models

| Item | Value |
| --- | --- |
| Dataset | benchmark/data/synthetic-dev |
| Dataset SHA-256 | cbd11d761bafef1645ba5bc16360212930bee27341b46adbb821c45916ee34e3 |
| Emails | 24 |
| Predictor failures | 0 |
| Latency p50 | 286.283 ms |
| Latency p95 | 399.715 ms |
| Peak heap | 111 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.21.0.3 |

## All emails (24 emails)

Macro F1: 0.716

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 28 | 28 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| date | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 24 | 22 | 2 | 2 | 0.917 | 0.917 | 0.917 | n/a |
| currentText | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| entity:PERSON | 16 | 13 | 11 | 3 | 0.542 | 0.813 | 0.650 | 0.500 |
| entity:COURSE | 6 | 2 | 6 | 4 | 0.250 | 0.333 | 0.286 | 0.722 |
| entity:DATE | 8 | 4 | 10 | 4 | 0.286 | 0.500 | 0.364 | 0.688 |
| entity:DEADLINE | 8 | 3 | 0 | 5 | 1.000 | 0.375 | 0.545 | 1.000 |
| entity:ID | 6 | 2 | 0 | 4 | 1.000 | 0.333 | 0.500 | 1.000 |
| speechActs | 24 | 14 | 16 | 10 | 0.467 | 0.583 | 0.519 | 0.500 |
| roleHint | 14 | 5 | 0 | 9 | 1.000 | 0.357 | 0.526 | 1.000 |
| language | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (12 emails)

Macro F1: 0.833

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 14 | 14 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| date | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 12 | 11 | 1 | 1 | 0.917 | 0.917 | 0.917 | n/a |
| currentText | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| entity:PERSON | 8 | 8 | 4 | 0 | 0.667 | 1.000 | 0.800 | 0.500 |
| entity:COURSE | 3 | 1 | 4 | 2 | 0.200 | 0.333 | 0.250 | 0.667 |
| entity:DATE | 4 | 4 | 1 | 0 | 0.800 | 1.000 | 0.889 | 0.875 |
| entity:DEADLINE | 4 | 3 | 0 | 1 | 1.000 | 0.750 | 0.857 | 1.000 |
| entity:ID | 3 | 2 | 0 | 1 | 1.000 | 0.667 | 0.800 | 1.000 |
| speechActs | 12 | 7 | 10 | 5 | 0.412 | 0.583 | 0.483 | 0.500 |
| roleHint | 7 | 5 | 0 | 2 | 1.000 | 0.714 | 0.833 | 1.000 |
| language | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: fr (12 emails)

Macro F1: 0.562

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 14 | 14 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| date | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 12 | 11 | 1 | 1 | 0.917 | 0.917 | 0.917 | n/a |
| currentText | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| entity:PERSON | 8 | 5 | 7 | 3 | 0.417 | 0.625 | 0.500 | 0.500 |
| entity:COURSE | 3 | 1 | 2 | 2 | 0.333 | 0.333 | 0.333 | 0.778 |
| entity:DATE | 4 | 0 | 9 | 4 | 0.000 | 0.000 | 0.000 | 0.500 |
| entity:DEADLINE | 4 | 0 | 0 | 4 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 12 | 7 | 6 | 5 | 0.538 | 0.583 | 0.560 | 0.500 |
| roleHint | 7 | 0 | 0 | 7 | n/a | 0.000 | 0.000 | 1.000 |
| language | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

