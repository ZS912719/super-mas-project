# Benchmark Report: metadata-rules

| Item | Value |
| --- | --- |
| Dataset | benchmark/data/synthetic-dev |
| Dataset SHA-256 | cbd11d761bafef1645ba5bc16360212930bee27341b46adbb821c45916ee34e3 |
| Emails | 24 |
| Predictor failures | 0 |
| Latency p50 | 2.123 ms |
| Latency p95 | 4.702 ms |
| Peak heap | 27 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.21.0.3 |

## All emails (24 emails)

Macro F1: 0.698

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 28 | 28 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| date | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 24 | 22 | 2 | 2 | 0.917 | 0.917 | 0.917 | n/a |
| currentText | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| entity:PERSON | 16 | 6 | 4 | 10 | 0.600 | 0.375 | 0.462 | 0.750 |
| entity:COURSE | 6 | 1 | 0 | 5 | 1.000 | 0.167 | 0.286 | 1.000 |
| entity:DATE | 8 | 4 | 10 | 4 | 0.286 | 0.500 | 0.364 | 0.688 |
| entity:DEADLINE | 8 | 3 | 0 | 5 | 1.000 | 0.375 | 0.545 | 1.000 |
| entity:ID | 6 | 2 | 0 | 4 | 1.000 | 0.333 | 0.500 | 1.000 |
| speechActs | 24 | 8 | 2 | 16 | 0.800 | 0.333 | 0.471 | 1.000 |
| roleHint | 14 | 5 | 0 | 9 | 1.000 | 0.357 | 0.526 | 1.000 |
| language | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (12 emails)

Macro F1: 0.867

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 14 | 14 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| date | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 12 | 11 | 1 | 1 | 0.917 | 0.917 | 0.917 | n/a |
| currentText | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| entity:PERSON | 8 | 6 | 2 | 2 | 0.750 | 0.750 | 0.750 | 0.750 |
| entity:COURSE | 3 | 1 | 0 | 2 | 1.000 | 0.333 | 0.500 | 1.000 |
| entity:DATE | 4 | 4 | 1 | 0 | 0.800 | 1.000 | 0.889 | 0.875 |
| entity:DEADLINE | 4 | 3 | 0 | 1 | 1.000 | 0.750 | 0.857 | 1.000 |
| entity:ID | 3 | 2 | 0 | 1 | 1.000 | 0.667 | 0.800 | 1.000 |
| speechActs | 12 | 8 | 2 | 4 | 0.800 | 0.667 | 0.727 | 1.000 |
| roleHint | 7 | 5 | 0 | 2 | 1.000 | 0.714 | 0.833 | 1.000 |
| language | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: fr (12 emails)

Macro F1: 0.455

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 14 | 14 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| date | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 12 | 11 | 1 | 1 | 0.917 | 0.917 | 0.917 | n/a |
| currentText | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| entity:PERSON | 8 | 0 | 2 | 8 | 0.000 | 0.000 | 0.000 | 0.750 |
| entity:COURSE | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DATE | 4 | 0 | 9 | 4 | 0.000 | 0.000 | 0.000 | 0.500 |
| entity:DEADLINE | 4 | 0 | 0 | 4 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 12 | 0 | 0 | 12 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 7 | 0 | 0 | 7 | n/a | 0.000 | 0.000 | 1.000 |
| language | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

