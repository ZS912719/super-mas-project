# Is a reply needed? (plan reply-needed)

Run 2026-11-11T20:51Z, mode `enron`, owners `-`. Positive class: no reply needed (the decision that suppresses a draft); `UNSURE` counts as reply needed.

| | Gold: no reply needed | Gold: reply needed |
| --- | --- | --- |
| System: no reply needed | 50 | **7** |
| System: reply needed or unsure | 12 | 15 |

Precision of "no reply needed": 50 / 57 (0.88) [95 % CI 0.78–0.95]; recall: 50 / 62 (0.81) [95 % CI 0.70–0.90]; accuracy: 65 / 84 (0.77) of 84 emails. Gold "unsure": 16 (left out; system said not needed for 10).

## Rule that decided

| Rule | Emails | Gold: no reply needed | Gold: reply needed | Gold: unsure |
| --- | --- | --- | --- | --- |
| NOT_NEEDED: no question, request or proposal: information only | 41 | 30 | 4 | 7 |
| NEEDED: asks a question | 12 | 6 | 5 | 1 |
| NEEDED: contains a request | 19 | 5 | 10 | 4 |
| NOT_NEEDED: broadcast | 15 | 10 | 2 | 3 |
| NOT_NEEDED: says it is automated or not to reply | 3 | 3 | 0 | 0 |
| NEEDED: contains a proposal | 2 | 1 | 0 | 1 |
| NOT_NEEDED: no text of its own | 7 | 6 | 1 | 0 |
| NOT_NEEDED: only thanks or an acknowledgement | 1 | 1 | 0 | 0 |

## Per email

| Email | Gold | System | Rule |
| --- | --- | --- | --- |
| enron-t0004-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0005-m01 | unsure | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0007-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0008-m01 | needed | NEEDED | asks a question |
| enron-t0009-m01 | unsure | NEEDED | contains a request |
| enron-t0010-m01 | unsure | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0011-m01 | needed | NOT_NEEDED | broadcast |
| enron-t0015-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0016-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0017-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0018-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0019-m01 | needed | NEEDED | contains a request |
| enron-t0025-m01 | not needed | NEEDED | asks a question |
| enron-t0028-m01 | needed | NEEDED | contains a request |
| enron-t0031-m01 | needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0032-m01 | not needed | NOT_NEEDED | says it is automated or not to reply |
| enron-t0033-m01 | not needed | NOT_NEEDED | says it is automated or not to reply |
| enron-t0034-m01 | not needed | NEEDED | contains a request |
| enron-t0036-m01 | needed | NEEDED | asks a question |
| enron-t0037-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0039-m01 | needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0042-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0044-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0046-m01 | not needed | NEEDED | asks a question |
| enron-t0048-m01 | unsure | NEEDED | contains a proposal |
| enron-t0049-m01 | needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0050-m01 | unsure | NOT_NEEDED | broadcast |
| enron-t0053-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0054-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0055-m01 | needed | NEEDED | contains a request |
| enron-t0056-m01 | needed | NEEDED | asks a question |
| enron-t0058-m01 | unsure | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0060-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0062-m01 | needed | NOT_NEEDED | no text of its own |
| enron-t0063-m01 | unsure | NEEDED | contains a request |
| enron-t0064-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0065-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0070-m01 | not needed | NEEDED | contains a request |
| enron-t0072-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0076-m01 | not needed | NEEDED | asks a question |
| enron-t0079-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0081-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0082-m01 | not needed | NOT_NEEDED | no text of its own |
| enron-t0083-m01 | needed | NEEDED | contains a request |
| enron-t0086-m01 | needed | NEEDED | asks a question |
| enron-t0087-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0090-m01 | unsure | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0093-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0094-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0100-m01 | not needed | NOT_NEEDED | says it is automated or not to reply |
| enron-t0106-m01 | unsure | NOT_NEEDED | broadcast |
| enron-t0108-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0110-m01 | not needed | NEEDED | asks a question |
| enron-t0111-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0112-m01 | not needed | NOT_NEEDED | only thanks or an acknowledgement |
| enron-t0113-m01 | needed | NEEDED | contains a request |
| enron-t0119-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0120-m01 | not needed | NEEDED | contains a request |
| enron-t0121-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0122-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0124-m01 | not needed | NEEDED | contains a request |
| enron-t0125-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0132-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0133-m01 | not needed | NOT_NEEDED | no text of its own |
| enron-t0134-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0138-m01 | not needed | NEEDED | asks a question |
| enron-t0142-m01 | unsure | NEEDED | contains a request |
| enron-t0146-m01 | unsure | NOT_NEEDED | broadcast |
| enron-t0148-m01 | unsure | NEEDED | contains a request |
| enron-t0150-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0154-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0159-m01 | not needed | NOT_NEEDED | no text of its own |
| enron-t0160-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0161-m01 | unsure | NEEDED | asks a question |
| enron-t0164-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0166-m01 | not needed | NEEDED | asks a question |
| enron-t0167-m01 | needed | NEEDED | contains a request |
| enron-t0169-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0170-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0171-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0173-m01 | not needed | NEEDED | contains a proposal |
| enron-t0174-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0175-m01 | needed | NEEDED | contains a request |
| enron-t0176-m01 | unsure | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0177-m01 | unsure | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0178-m01 | needed | NEEDED | contains a request |
| enron-t0179-m01 | unsure | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0180-m01 | not needed | NOT_NEEDED | broadcast |
| enron-t0182-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0184-m01 | not needed | NOT_NEEDED | no text of its own |
| enron-t0185-m01 | needed | NEEDED | contains a request |
| enron-t0188-m01 | needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0190-m01 | needed | NOT_NEEDED | broadcast |
| enron-t0191-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0194-m01 | not needed | NEEDED | contains a request |
| enron-t0195-m01 | not needed | NOT_NEEDED | no question, request or proposal: information only |
| enron-t0196-m01 | not needed | NOT_NEEDED | no text of its own |
| enron-t0198-m01 | needed | NEEDED | asks a question |
| enron-t0199-m01 | needed | NEEDED | contains a request |
| enron-t0200-m01 | not needed | NOT_NEEDED | no text of its own |
