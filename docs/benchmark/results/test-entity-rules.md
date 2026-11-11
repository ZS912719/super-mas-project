# Benchmark Report: entity-rules

| Item | Value |
| --- | --- |
| Dataset | benchmark/data/enron/labeling/frozen-test |
| Dataset SHA-256 | 2f02ed8f0847275836a6de29dc60fc3767a93c3bd289f3e115ce1cae1b20fa21 |
| Emails | 100 |
| Predictor failures | 0 |
| Latency p50 | 0.991 ms |
| Latency p95 | 4.053 ms |
| Peak heap | 27 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.21.0.3 |

## All emails (100 emails)

Macro F1: 0.570

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 634 | 634 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 99 | 1 | 1 | 0.990 | 0.990 | 0.990 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 94 | 53 | 40 | 41 | 0.570 | 0.564 | 0.567 | 0.833 |
| entity:PERSON | 81 | 4 | 9 | 77 | 0.308 | 0.049 | 0.085 | 0.931 |
| entity:COURSE | 0 | 0 | 4 | 0 | 0.000 | n/a | 0.000 | 0.970 |
| entity:DATE | 67 | 27 | 28 | 40 | 0.491 | 0.403 | 0.443 | 0.968 |
| entity:DEADLINE | 2 | 0 | 2 | 2 | 0.000 | 0.000 | 0.000 | 0.980 |
| entity:ID | 8 | 1 | 2 | 7 | 0.333 | 0.125 | 0.182 | 0.978 |
| speechActs | 77 | 0 | 0 | 77 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (100 emails)

Macro F1: 0.570

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 634 | 634 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 99 | 1 | 1 | 0.990 | 0.990 | 0.990 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 94 | 53 | 40 | 41 | 0.570 | 0.564 | 0.567 | 0.833 |
| entity:PERSON | 81 | 4 | 9 | 77 | 0.308 | 0.049 | 0.085 | 0.931 |
| entity:COURSE | 0 | 0 | 4 | 0 | 0.000 | n/a | 0.000 | 0.970 |
| entity:DATE | 67 | 27 | 28 | 40 | 0.491 | 0.403 | 0.443 | 0.968 |
| entity:DEADLINE | 2 | 0 | 2 | 2 | 0.000 | 0.000 | 0.000 | 0.980 |
| entity:ID | 8 | 1 | 2 | 7 | 0.333 | 0.125 | 0.182 | 0.978 |
| speechActs | 77 | 0 | 0 | 77 | n/a | 0.000 | 0.000 | 1.000 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

