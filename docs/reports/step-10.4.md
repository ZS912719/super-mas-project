# Step 10.4: Discovered Topics Against Declared Topics and C1

## Outcome

**Discovered topics are much better than the folder-name topics of step 4.10, but they still lose to plain search (C1).** On the folder proxy, the one comparison not decided by construction:

| Variant | Recall@10 | MRR | nDCG@10 |
| --- | --- | --- | --- |
| C1 | **0.113** (0.099-0.127) | **0.687** (0.645-0.727) | **0.454** (0.420-0.488) |
| Declared topics (C3, step 4.10) | 0.020 (0.012-0.031) | 0.121 (0.090-0.152) | 0.068 (0.049-0.089) |
| Discovered topics | 0.070 (0.060-0.082) | 0.615 (0.572-0.659) | 0.325 (0.296-0.354) |
| Declared + discussion + discovered | 0.073 (0.063-0.086) | 0.631 (0.589-0.674) | 0.337 (0.308-0.367) |

Paired against C1, discovered topics are −0.043 Recall@10 (−0.052 to −0.034). The fair baseline that step 4.10 said was missing, **C1 plus thread history**, matches the best thread-proxy result (Recall@10 0.988, MRR 1.000) and equals C1 on the folder proxy, as it must, because own-thread results are removed there.

Full results: `docs/benchmark/results/c3-discovered-enron.md`. Isolation: 0 violations in 2,742 cross-project and 914 forged-sender probes. The C1 column reproduces step 4.10 on all metrics, which shows the setup is unchanged.

## Discovery on real mail

| Item | Value |
| --- | --- |
| Projects | 7 Enron mailboxes, 10,206 messages, 8,215 threads |
| Topics found | 214 (about 31 per mailbox) |
| Threads in a topic | 7,162 of 8,215 (87%) |
| Discovery plus retagging, all 7 projects | 9.6 s on the development laptop |
| Emails where the discovered agent fits | 887 of 914 (97%) |

Coverage is the big change from declared topics. Step 4.10's folder topics fit about 3% of emails; discovered topics fit 97%, with no configuration.

## How to read the result

- **The topic filter is the likely cause of the gap to C1.** The agent searches only inside a topic's documents. C1 searches all permitted mail, so relevant mail outside the email's two best topics is out of reach for the agent. This is my reading; I did not test the alternative.
- **Discovered topics hurt the thread ranking when combined.** On the thread proxy, discussion alone has MRR 1.000; adding discovered evidence lowers it to 0.788, because RRF interleaves the topic lists with the thread list. The merge treats all lists as equal.
- **The folder proxy is the same weak proxy as before**: human filing, not relevance labels.

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Settings from step 10.1, unchanged after seeing results | Same rule as step 4.10: tuning to the proxy gives a better number with no meaning | Trying other `k`, thresholds or topics per email until C1 is beaten |
| Same queries and code as step 4.10, new variants added | Results are directly comparable, and the C1 column is a control | A new benchmark |
| "C1 + thread history": thread list first, then C1 without duplicates | The intended C1 design includes `retrieveThread` (step 4.10's critical point) | Fusing them with RRF, which is a design choice of its own |
| Activation counted on the thread proxy only | Same rule as the C3 statistics in step 4.10 | Counting both proxies twice |

## Problems encountered

- **Mixed statistics, caught before the run.** The first version of the change passed the "record" flag to the discovered-only variant, which would have mixed its latency and agent counts into the C3 statistics. A separate flag now counts activation only.

## Critical points

- **Discovery saw all mail, including mail later than each query.** In use, topics would be built from earlier mail only. This makes discovered topics look slightly better than they would be, and they still lose to C1, so the direction of the conclusion is safe.
- **No human relevance labels, one seed, the first 7 mailboxes alphabetically.** As in step 4.10.
- **Keywords were not inspected.** In line with the earlier rule of not reading Enron content in bulk, I report counts only, so I cannot say how readable the 214 topics are to a person.
- **Untested idea:** use the topic as a ranking boost on C1 rather than a filter. That keeps C1's reach and adds topic structure. It must be tested on labelled data, not tuned on these proxies.

## What this means for the design

- **Retrieval default:** C1 plus thread history gives the best measured results. I recommend it as the default context for the reply agents.
- **Discovered topics** are fully automatic, as the project owner asked, and far better than declared folder topics, but they do not improve retrieval over C1 on this evidence. Keep them as the automatic grouping of mail (useful for organizing and for a UI) and as an optional context agent, switched on by configuration, until labelled data shows a gain.
- **The merge should weight the thread list above topic lists** if discovered topics are used with the discussion agent. That is a design change for the team's discussion, not something to tune on these proxies.
