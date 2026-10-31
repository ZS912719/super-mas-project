# Benchmark Report: facts

| Item | Value |
| --- | --- |
| Dataset | benchmark/data/synthetic-dev |
| Dataset SHA-256 | cbd11d761bafef1645ba5bc16360212930bee27341b46adbb821c45916ee34e3 |
| Emails | 24 |
| Predictor failures | 0 |
| Latency p50 | 0.710 ms |
| Latency p95 | 3.046 ms |
| Peak heap | 62 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.20.1 |

## All emails (24 emails)

Macro F1: 0.455

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 28 | 28 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| date | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 24 | 22 | 2 | 2 | 0.917 | 0.917 | 0.917 | n/a |
| currentText | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| entity:PERSON | 16 | 0 | 0 | 16 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 6 | 0 | 0 | 6 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DATE | 8 | 0 | 0 | 8 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 8 | 0 | 0 | 8 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 6 | 0 | 0 | 6 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 24 | 0 | 0 | 24 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 14 | 0 | 0 | 14 | n/a | 0.000 | 0.000 | 1.000 |
| language | 24 | 24 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (12 emails)

Macro F1: 0.455

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 14 | 14 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| date | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| threadId | 12 | 11 | 1 | 1 | 0.917 | 0.917 | 0.917 | n/a |
| currentText | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| entity:PERSON | 8 | 0 | 0 | 8 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DATE | 4 | 0 | 0 | 4 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 4 | 0 | 0 | 4 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 12 | 0 | 0 | 12 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 7 | 0 | 0 | 7 | n/a | 0.000 | 0.000 | 1.000 |
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
| entity:PERSON | 8 | 0 | 0 | 8 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DATE | 4 | 0 | 0 | 4 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 4 | 0 | 0 | 4 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 3 | 0 | 0 | 3 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 12 | 0 | 0 | 12 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 7 | 0 | 0 | 7 | n/a | 0.000 | 0.000 | 1.000 |
| language | 12 | 12 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

