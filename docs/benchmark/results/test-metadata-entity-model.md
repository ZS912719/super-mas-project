# Benchmark Report: metadata-entity-model

| Item | Value |
| --- | --- |
| Dataset | benchmark/data/enron/labeling/frozen-test |
| Dataset SHA-256 | 2f02ed8f0847275836a6de29dc60fc3767a93c3bd289f3e115ce1cae1b20fa21 |
| Emails | 100 |
| Predictor failures | 0 |
| Latency p50 | 249.152 ms |
| Latency p95 | 1471.275 ms |
| Peak heap | 110 MB |
| Machine | Linux, 8 logical CPUs |
| Java | 17.0.21.0.3 |

## All emails (100 emails)

Macro F1: 0.663

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 634 | 634 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 99 | 1 | 1 | 0.990 | 0.990 | 0.990 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 94 | 53 | 40 | 41 | 0.570 | 0.564 | 0.567 | 0.833 |
| entity:PERSON | 81 | 64 | 46 | 17 | 0.582 | 0.790 | 0.670 | 0.638 |
| entity:COURSE | 0 | 0 | 5 | 0 | 0.000 | n/a | 0.000 | 0.960 |
| entity:DATE | 67 | 27 | 28 | 40 | 0.491 | 0.403 | 0.443 | 0.968 |
| entity:DEADLINE | 2 | 0 | 2 | 2 | 0.000 | 0.000 | 0.000 | 0.980 |
| entity:ID | 8 | 1 | 10 | 7 | 0.091 | 0.125 | 0.105 | 0.957 |
| speechActs | 77 | 40 | 38 | 37 | 0.513 | 0.519 | 0.516 | 0.727 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

## Language: en (100 emails)

Macro F1: 0.663

| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sender | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| recipients | 634 | 634 | 0 | 0 | 1.000 | 1.000 | 1.000 | 1.000 |
| date | 100 | 99 | 1 | 1 | 0.990 | 0.990 | 0.990 | n/a |
| threadId | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |
| currentText | 94 | 53 | 40 | 41 | 0.570 | 0.564 | 0.567 | 0.833 |
| entity:PERSON | 81 | 64 | 46 | 17 | 0.582 | 0.790 | 0.670 | 0.638 |
| entity:COURSE | 0 | 0 | 5 | 0 | 0.000 | n/a | 0.000 | 0.960 |
| entity:DATE | 67 | 27 | 28 | 40 | 0.491 | 0.403 | 0.443 | 0.968 |
| entity:DEADLINE | 2 | 0 | 2 | 2 | 0.000 | 0.000 | 0.000 | 0.980 |
| entity:ID | 8 | 1 | 10 | 7 | 0.091 | 0.125 | 0.105 | 0.957 |
| speechActs | 77 | 40 | 38 | 37 | 0.513 | 0.519 | 0.516 | 0.727 |
| roleHint | 0 | 0 | 0 | 0 | n/a | n/a | n/a | 1.000 |
| language | 100 | 100 | 0 | 0 | 1.000 | 1.000 | 1.000 | n/a |

