# Missing Information (benchmark plan C10)

Run 2026-11-11T11:28Z. Prototype on the synthetic sets: blackboard, template generation, rules-only metadata and missing-information rules plus knowledge requirements. Listed items are the missing items of the latest contributions (what the composer merges), compared with the frozen `mustAsk` checks (`reply-gold/v1`, not yet reviewed by a person).

| Measure | Value |
| --- | --- |
| Reply-expected emails | 22 |
| Gold asks found in the plans (recall) | 3 / 6 (0.50) |
| Emails with a gold ask | 5 |
| Emails with no gold ask but a listed item (over-asking) | 5 of 17 |
| No-reply emails with a listed item | 2 of 14 |

## Per email (reply expected, or with a listed item)

| Set | Email | Gold asks | Found | Missed | Listed by the plans |
| --- | --- | --- | --- | --- | --- |
| dev | en-01 | medical-certificate | medical-certificate |  | medical certificate (required by student-faq) |
| dev | en-02 (no reply) |  |  |  | COURSE |
| dev | en-04 (no reply) |  |  |  | ID |
| dev | en-05 |  |  |  |  |
| dev | en-08 |  |  |  |  |
| dev | en-10 |  |  |  | COURSE |
| dev | en-12 |  |  |  |  |
| dev | fr-01 | medical-certificate |  | medical-certificate |  |
| dev | fr-05 |  |  |  |  |
| dev | fr-08 |  |  |  |  |
| dev | fr-10 |  |  |  |  |
| dev | fr-12 |  |  |  |  |
| multi | en-m01 | medical-certificate | medical-certificate |  | medical certificate (required by student-faq); ID |
| multi | en-m02 |  |  |  | COURSE |
| multi | en-m03 |  |  |  |  |
| multi | en-m04 |  |  |  | COURSE; medical certificate (required by student-faq) |
| multi | en-m05 |  |  |  |  |
| multi | en-m06 | clarify |  | clarify |  |
| multi | en-m07 |  |  |  | COURSE |
| multi | en-m08 |  |  |  | COURSE |
| multi | en-m09 |  |  |  |  |
| multi | en-m10 |  |  |  |  |
| multi | en-m11 | student-number, medical-certificate | medical-certificate | student-number | COURSE; medical certificate (required by student-faq) |
| multi | en-m12 |  |  |  |  |
