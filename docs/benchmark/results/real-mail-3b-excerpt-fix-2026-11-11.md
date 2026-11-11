# Replies on Real Mail (benchmark plan W9)

Run 2026-11-11T18:10Z on `benchmark/data/enron/labeling/frozen-test` (100 Enron emails, frozen test set). Model: ONLINE model meta-llama/llama-3.2-3b-instruct at https://openrouter.ai/api/v1 (development stand-in: email text is sent to openrouter.ai), recording replies to runtime/real-mail/3b-excerpt-fix-2026-11-11/llm-cache. Blackboard architecture, university knowledge. No gold answer: only checks that need none. Counts and email identifiers only; email and reply text stay in the Git-ignored run folder.

| Measure | Value |
| --- | --- |
| Drafted | 100 / 100 |
| Latency p50 / p95 (drafted) | 1.1 s / 2.7 s |
| Greeting names the sender | 83 / 100 |
| Greeting names only a recipient | 2 |
| Replies without any flag | 78 / 100 |
| Promise or claimed action | 0 |
| University knowledge in a reply | 0 |
| Another email's content (shared 6-word sequence) | 3 |

| Check violation (reply checks with no facts) | Replies |
| --- | --- |
| placeholder | 1 |
| wrong-greeting | 4 |

| Who answered | Emails |
| --- | --- |
| generic | 100 |

| Email | Status | By | Flags |
| --- | --- | --- | --- |
| enron-t0004-m01 | AWAITING_REVIEW | generic | none |
| enron-t0005-m01 | AWAITING_REVIEW | generic | none |
| enron-t0007-m01 | AWAITING_REVIEW | generic | none |
| enron-t0008-m01 | AWAITING_REVIEW | generic | none |
| enron-t0009-m01 | AWAITING_REVIEW | generic | none |
| enron-t0010-m01 | AWAITING_REVIEW | generic | none |
| enron-t0011-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0015-m01 | AWAITING_REVIEW | generic | wrong-greeting |
| enron-t0016-m01 | AWAITING_REVIEW | generic | none |
| enron-t0017-m01 | AWAITING_REVIEW | generic | placeholder |
| enron-t0018-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0019-m01 | AWAITING_REVIEW | generic | another email's content |
| enron-t0025-m01 | AWAITING_REVIEW | generic | none |
| enron-t0028-m01 | AWAITING_REVIEW | generic | none |
| enron-t0031-m01 | AWAITING_REVIEW | generic | none |
| enron-t0032-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0033-m01 | AWAITING_REVIEW | generic | none |
| enron-t0034-m01 | AWAITING_REVIEW | generic | none |
| enron-t0036-m01 | AWAITING_REVIEW | generic | sender not named in greeting; another email's content |
| enron-t0037-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0039-m01 | AWAITING_REVIEW | generic | none |
| enron-t0042-m01 | AWAITING_REVIEW | generic | none |
| enron-t0044-m01 | AWAITING_REVIEW | generic | none |
| enron-t0046-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0048-m01 | AWAITING_REVIEW | generic | none |
| enron-t0049-m01 | AWAITING_REVIEW | generic | none |
| enron-t0050-m01 | AWAITING_REVIEW | generic | none |
| enron-t0053-m01 | AWAITING_REVIEW | generic | none |
| enron-t0054-m01 | AWAITING_REVIEW | generic | none |
| enron-t0055-m01 | AWAITING_REVIEW | generic | none |
| enron-t0056-m01 | AWAITING_REVIEW | generic | none |
| enron-t0058-m01 | AWAITING_REVIEW | generic | none |
| enron-t0060-m01 | AWAITING_REVIEW | generic | none |
| enron-t0062-m01 | AWAITING_REVIEW | generic | none |
| enron-t0063-m01 | AWAITING_REVIEW | generic | none |
| enron-t0064-m01 | AWAITING_REVIEW | generic | none |
| enron-t0065-m01 | AWAITING_REVIEW | generic | none |
| enron-t0070-m01 | AWAITING_REVIEW | generic | wrong-greeting; greets a recipient |
| enron-t0072-m01 | AWAITING_REVIEW | generic | none |
| enron-t0076-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0079-m01 | AWAITING_REVIEW | generic | none |
| enron-t0081-m01 | AWAITING_REVIEW | generic | none |
| enron-t0082-m01 | AWAITING_REVIEW | generic | none |
| enron-t0083-m01 | AWAITING_REVIEW | generic | none |
| enron-t0086-m01 | AWAITING_REVIEW | generic | none |
| enron-t0087-m01 | AWAITING_REVIEW | generic | none |
| enron-t0090-m01 | AWAITING_REVIEW | generic | none |
| enron-t0093-m01 | AWAITING_REVIEW | generic | none |
| enron-t0094-m01 | AWAITING_REVIEW | generic | wrong-greeting |
| enron-t0100-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0106-m01 | AWAITING_REVIEW | generic | none |
| enron-t0108-m01 | AWAITING_REVIEW | generic | none |
| enron-t0110-m01 | AWAITING_REVIEW | generic | none |
| enron-t0111-m01 | AWAITING_REVIEW | generic | another email's content |
| enron-t0112-m01 | AWAITING_REVIEW | generic | none |
| enron-t0113-m01 | AWAITING_REVIEW | generic | none |
| enron-t0119-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0120-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0121-m01 | AWAITING_REVIEW | generic | none |
| enron-t0122-m01 | AWAITING_REVIEW | generic | wrong-greeting; greets a recipient |
| enron-t0124-m01 | AWAITING_REVIEW | generic | none |
| enron-t0125-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0132-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0133-m01 | AWAITING_REVIEW | generic | none |
| enron-t0134-m01 | AWAITING_REVIEW | generic | none |
| enron-t0138-m01 | AWAITING_REVIEW | generic | none |
| enron-t0142-m01 | AWAITING_REVIEW | generic | none |
| enron-t0146-m01 | AWAITING_REVIEW | generic | none |
| enron-t0148-m01 | AWAITING_REVIEW | generic | none |
| enron-t0150-m01 | AWAITING_REVIEW | generic | none |
| enron-t0154-m01 | AWAITING_REVIEW | generic | none |
| enron-t0159-m01 | AWAITING_REVIEW | generic | none |
| enron-t0160-m01 | AWAITING_REVIEW | generic | none |
| enron-t0161-m01 | AWAITING_REVIEW | generic | none |
| enron-t0164-m01 | AWAITING_REVIEW | generic | none |
| enron-t0166-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0167-m01 | AWAITING_REVIEW | generic | none |
| enron-t0169-m01 | AWAITING_REVIEW | generic | none |
| enron-t0170-m01 | AWAITING_REVIEW | generic | none |
| enron-t0171-m01 | AWAITING_REVIEW | generic | none |
| enron-t0173-m01 | AWAITING_REVIEW | generic | none |
| enron-t0174-m01 | AWAITING_REVIEW | generic | none |
| enron-t0175-m01 | AWAITING_REVIEW | generic | none |
| enron-t0176-m01 | AWAITING_REVIEW | generic | none |
| enron-t0177-m01 | AWAITING_REVIEW | generic | none |
| enron-t0178-m01 | AWAITING_REVIEW | generic | none |
| enron-t0179-m01 | AWAITING_REVIEW | generic | none |
| enron-t0180-m01 | AWAITING_REVIEW | generic | none |
| enron-t0182-m01 | AWAITING_REVIEW | generic | none |
| enron-t0184-m01 | AWAITING_REVIEW | generic | none |
| enron-t0185-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0188-m01 | AWAITING_REVIEW | generic | none |
| enron-t0190-m01 | AWAITING_REVIEW | generic | none |
| enron-t0191-m01 | AWAITING_REVIEW | generic | none |
| enron-t0194-m01 | AWAITING_REVIEW | generic | none |
| enron-t0195-m01 | AWAITING_REVIEW | generic | sender not named in greeting |
| enron-t0196-m01 | AWAITING_REVIEW | generic | none |
| enron-t0198-m01 | AWAITING_REVIEW | generic | none |
| enron-t0199-m01 | AWAITING_REVIEW | generic | none |
| enron-t0200-m01 | AWAITING_REVIEW | generic | none |
