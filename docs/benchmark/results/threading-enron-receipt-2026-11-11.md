# Threading on Enron without reply headers (benchmark plan C02)

Run 2026-11-11T19:31Z on `benchmark/data/enron-raw/maildir`, mailboxes in name order until 10000 unique messages (as C1, B08). No thread gold: the table counts threads and merges only.

Joined: messages put into an earlier message's thread. Quoting: of those, how many contain an 8-word sequence of an earlier message of that thread (quoted text, strong evidence of a real reply; a lower bound of precision, since short replies often quote nothing).

| Mailbox | Messages | Threads, references only | Threads, unrestricted fallback | Joined (unrestricted) | Quoting (unrestricted) | Threads, restricted on receipt | Joined (restricted) | Quoting (restricted) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 1397 | 1397 | 1188 | 208 | 140 | 1318 | 79 | 52 |
| 2 | 2643 | 2643 | 2125 | 515 | 430 | 2336 | 307 | 265 |
| 3 | 492 | 492 | 441 | 51 | 43 | 481 | 11 | 10 |
| 4 | 324 | 324 | 309 | 15 | 13 | 316 | 8 | 8 |
| 5 | 461 | 461 | 300 | 161 | 141 | 362 | 99 | 82 |
| 6 | 3084 | 3084 | 2236 | 840 | 646 | 2628 | 454 | 380 |
| 7 | 1805 | 1805 | 1616 | 189 | 170 | 1723 | 82 | 77 |
| All | 10206 | 10206 | 8215 | 1979 | 1583 | 9164 | 1040 | 874 |
