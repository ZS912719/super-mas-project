# Router Against Blackboard

Step 12.7, run on 2026-11-07. Rules-only metadata, template generation (no language model), SQLite board, fresh data per set and architecture. See `docs/reports/step-12.7.md`.

## Single-intent emails (`synthetic-dev`, 24, gold role from the labelled role hint)

| Measure | Router | Blackboard |
| --- | --- | --- |
| Answering agents equal the gold role | 15 of 24 | 15 of 24 |
| Gold role among the answering agents | 15 of 24 | 15 of 24 |
| Drafts awaiting review | 24 of 24 | 24 of 24 |
| Mean answering agents per email | 1.00 | 1.00 |
| Mean rounds per email | 4.00 | 6.00 |
| Mean SQLite revisions per email | 20.29 | 38.29 |
| Median time per email (ms) | 64.05 | 178.21 |

## Multi-request emails (`synthetic-multi`, 12)

| Measure | Router | Blackboard |
| --- | --- | --- |
| Answering agents equal the gold set | 4 of 12 | 5 of 12 |
| Role precision (micro) | 0.83 | 0.92 |
| Role recall (micro) | 0.48 | 0.57 |
| Request count equals the gold count | n/a (no request level), 0 of 12 | 11 of 12 |
| Every request answered by a contribution | n/a, 0 of 12 | 11 of 12 |
| Mean rounds per email | 4.00 | 6.08 |
| Mean SQLite revisions per email | 22.25 | 40.92 |
| Median time per email (ms) | 66.86 | 148.70 |

## Sensitivity of the contribution threshold (blackboard)

The default (0.4) was fixed before these sets existed. Lower thresholds let weaker specialists join.

| Threshold | Single-intent exact | Multi exact | Multi precision | Multi recall | Multi mean rounds |
| --- | --- | --- | --- | --- | --- |
| 0.1 | 10 of 24 | 9 of 12 | 0.88 | 1.00 | 7.00 |
| 0.2 | 16 of 24 | 10 of 12 | 1.00 | 0.90 | 6.58 |
| 0.3 | 15 of 24 | 5 of 12 | 0.92 | 0.57 | 6.08 |
| 0.4 | 15 of 24 | 5 of 12 | 0.92 | 0.57 | 6.08 |
| 0.5 | 15 of 24 | 3 of 12 | 0.50 | 0.29 | 6.00 |

### Per email (multi-request)

| Email | Gold | Router | Blackboard | Requests (gold) | Rounds R / B |
| --- | --- | --- | --- | --- | --- |
| en-m01 | [administration, student] | [generic] | [administration, student] | 2 (2) | 4 / 7 |
| en-m02 | [research, teacher] | [teacher] | [teacher] | 2 (2) | 4 / 6 |
| en-m03 | [administration, research] | [research] | [research] | 2 (2) | 4 / 6 |
| en-m04 | [student] | [student] | [student] | 2 (2) | 4 / 6 |
| en-m05 | [administration, teacher] | [generic] | [generic] | 2 (2) | 4 / 6 |
| en-m06 | [generic] | [generic] | [generic] | 1 (1) | 4 / 6 |
| en-m07 | [research, student] | [student] | [student] | 1 (2) | 4 / 6 |
| en-m08 | [teacher] | [teacher] | [teacher] | 2 (2) | 4 / 6 |
| en-m09 | [administration, research] | [research] | [research] | 2 (2) | 4 / 6 |
| en-m10 | [generic] | [generic] | [generic] | 1 (1) | 4 / 6 |
| en-m11 | [administration, research, student] | [student] | [student] | 3 (3) | 4 / 6 |
| en-m12 | [administration, research] | [research] | [research] | 2 (2) | 4 / 6 |

### Per email (single-intent, mismatches only)

| Email | Gold | Router | Blackboard |
| --- | --- | --- | --- |
| en-07 | [administration] | [generic] | [generic] |
| en-09 | [teacher] | [generic] | [generic] |
| fr-01 | [student] | [generic] | [generic] |
| fr-02 | [teacher] | [generic] | [generic] |
| fr-04 | [administration] | [generic] | [generic] |
| fr-05 | [research] | [generic] | [generic] |
| fr-07 | [administration] | [generic] | [generic] |
| fr-09 | [teacher] | [generic] | [generic] |
| fr-10 | [student] | [generic] | [generic] |
