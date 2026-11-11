# Benchmark Report: facts

| Item | Value |
| --- | --- |
| Dataset | benchmark/data/enron/labeling/frozen-test |
| Dataset SHA-256 | 2f02ed8f0847275836a6de29dc60fc3767a93c3bd289f3e115ce1cae1b20fa21 |
| Emails | 100 |
| Predictor failures | 0 |
| Latency p50 | 0.814 ms |
| Latency p95 | 2.672 ms |
| Peak heap | 27 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.21.0.3 |

## All emails (100 emails)

Macro F1: 0.505

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 634 | 634 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 99 | 1 | 1 | 0.990 | 0.990 | 0.990 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 94 | 53 | 40 | 41 | 0.570 | 0.564 | 0.567 | 0.833 |
| entity:PERSON | 81 | 0 | 0 | 81 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| entity:DATE | 67 | 0 | 0 | 67 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 2 | 0 | 0 | 2 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 8 | 0 | 0 | 8 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 77 | 0 | 0 | 77 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (100 emails)

Macro F1: 0.505

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 634 | 634 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 99 | 1 | 1 | 0.990 | 0.990 | 0.990 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 94 | 53 | 40 | 41 | 0.570 | 0.564 | 0.567 | 0.833 |
| entity:PERSON | 81 | 0 | 0 | 81 | n/a | 0.000 | 0.000 | 1.000 |
| entity:COURSE | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| entity:DATE | 67 | 0 | 0 | 67 | n/a | 0.000 | 0.000 | 1.000 |
| entity:DEADLINE | 2 | 0 | 0 | 2 | n/a | 0.000 | 0.000 | 1.000 |
| entity:ID | 8 | 0 | 0 | 8 | n/a | 0.000 | 0.000 | 1.000 |
| speechActs | 77 | 0 | 0 | 77 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

