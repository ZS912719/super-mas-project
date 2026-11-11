# Is a reply needed? (plan reply-needed)

Run 2026-11-11T20:39Z, mode `synthetic`, owners `e.martin@example.edu,c.dubois@example.fr`. Positive class: no reply needed (the decision that suppresses a draft); `UNSURE` counts as reply needed.

| | Gold: no reply needed | Gold: reply needed |
| --- | --- | --- |
| System: no reply needed | 14 | **1** |
| System: reply needed or unsure | 0 | 21 |

Precision of "no reply needed": 14 / 15 (0.93) [95 % CI 0.78–1.00]; recall: 14 / 14 (1.00) [95 % CI 1.00–1.00]; accuracy: 35 / 36 (0.97) of 36 emails.

## Rule that decided

| Rule | Emails | Gold: no reply needed | Gold: reply needed | Gold: unsure |
| --- | --- | --- | --- | --- |
| NEEDED: asks a question | 20 | 0 | 20 | 0 |
| NOT_NEEDED: sent by the mailbox owner | 2 | 2 | 0 | 0 |
| NOT_NEEDED: only thanks or an acknowledgement | 2 | 2 | 0 | 0 |
| NOT_NEEDED: broadcast | 2 | 2 | 0 | 0 |
| NOT_NEEDED: no question, request or proposal: information only | 9 | 8 | 1 | 0 |
| NEEDED: contains a request | 1 | 0 | 1 | 0 |

## Per email

| Email | Gold | System | Rule |
| --- | --- | --- | --- |
| en-01 | needed | NEEDED | asks a question |
| en-02 | not needed | NOT_NEEDED | sent by the mailbox owner |
| en-03 | not needed | NOT_NEEDED | only thanks or an acknowledgement |
| en-04 | not needed | NOT_NEEDED | broadcast |
| en-05 | needed | NEEDED | asks a question |
| en-06 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| en-07 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| en-08 | needed | NEEDED | asks a question |
| en-09 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| en-10 | needed | NEEDED | asks a question |
| en-11 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| en-12 | needed | NEEDED | contains a request |
| fr-01 | needed | NEEDED | asks a question |
| fr-02 | not needed | NOT_NEEDED | sent by the mailbox owner |
| fr-03 | not needed | NOT_NEEDED | only thanks or an acknowledgement |
| fr-04 | not needed | NOT_NEEDED | broadcast |
| fr-05 | needed | NEEDED | asks a question |
| fr-06 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| fr-07 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| fr-08 | needed | NEEDED | asks a question |
| fr-09 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| fr-10 | needed | NEEDED | asks a question |
| fr-11 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| fr-12 | needed | NOT_NEEDED | no question, request or proposal: information only |
| en-m01 | needed | NEEDED | asks a question |
| en-m02 | needed | NEEDED | asks a question |
| en-m03 | needed | NEEDED | asks a question |
| en-m04 | needed | NEEDED | asks a question |
| en-m05 | needed | NEEDED | asks a question |
| en-m06 | needed | NEEDED | asks a question |
| en-m07 | needed | NEEDED | asks a question |
| en-m08 | needed | NEEDED | asks a question |
| en-m09 | needed | NEEDED | asks a question |
| en-m10 | needed | NEEDED | asks a question |
| en-m11 | needed | NEEDED | asks a question |
| en-m12 | needed | NEEDED | asks a question |
