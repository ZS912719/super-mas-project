# Human rubric on real-mail replies (plan W6/W9)

One rater (the project owner, R1), blind (no flags, model or run shown), on the labelling page. The 20 replies of the 3 B run after the excerpt fix (B62) to the emails sampled with seed 20261111. Scores 0-2 with the anchors of `label_server.py` (`RUBRIC`); n/a only for "admits limits". The rater's notes stay in the Git-ignored run folder; here only scores and two note categories (a note saying a reply or response was not needed; a note saying the reply repeats or rewrites the email).

| Criterion | 0 | 1 | 2 | n/a | Mean |
| --- | --- | --- | --- | --- | --- |
| polite | 0 | 0 | 20 | 0 | 2.00 |
| answers | 14 | 1 | 5 | 0 | 0.55 |
| inventsNothing | 1 | 1 | 18 | 0 | 1.85 |
| admitsLimits | 2 | 1 | 2 | 15 | 1.00 |

Note "no reply needed": 10 of 20 emails (enron-t0032-m01, enron-t0037-m01, enron-t0046-m01, enron-t0100-m01, enron-t0111-m01, enron-t0138-m01, enron-t0164-m01, enron-t0169-m01, enron-t0191-m01, enron-t0194-m01); their "answers" scores: 0, 0, 0, 0, 0, 0, 0, 2, 0, 0.
Note "repeats or rewrites the email": 3 (enron-t0034-m01, enron-t0058-m01, enron-t0060-m01).
"answers" on the other 10 emails: mean 0.90 (scores 2, 0, 0, 0, 2, 0, 0, 1, 2, 2).

| Email | Polite | Answers | Invents nothing | Admits limits | Note category |
| --- | --- | --- | --- | --- | --- |
| enron-t0025-m01 | 2 | 2 | 2 | n/a |  |
| enron-t0032-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0034-m01 | 2 | 0 | 0 | 0 | repeats the email |
| enron-t0037-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0046-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0058-m01 | 2 | 0 | 2 | 0 | repeats the email |
| enron-t0060-m01 | 2 | 0 | 2 | 1 | repeats the email |
| enron-t0062-m01 | 2 | 2 | 2 | n/a |  |
| enron-t0064-m01 | 2 | 0 | 2 | n/a |  |
| enron-t0094-m01 | 2 | 0 | 2 | n/a |  |
| enron-t0100-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0111-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0138-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0142-m01 | 2 | 1 | 2 | n/a |  |
| enron-t0164-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0169-m01 | 2 | 2 | 2 | n/a | no reply needed |
| enron-t0188-m01 | 2 | 2 | 1 | 2 |  |
| enron-t0191-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0194-m01 | 2 | 0 | 2 | n/a | no reply needed |
| enron-t0196-m01 | 2 | 2 | 2 | 2 |  |

Time: median 42 s per email, 16 min in all.
