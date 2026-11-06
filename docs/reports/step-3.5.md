# Step 3.5: The C1 Baseline

## Outcome

**C1 is now measured, and Phase 3 is complete, but its domain search does worse than a simple recency baseline on the proxy used here.** Recall@10 is 0.338 against 0.601. Isolation held, with zero violations across all probes. The plan sets no pass mark for Phase 3 beyond measuring the baseline, so this does not block Phase 4, but it does set a low bar that needs careful reading.

## What was built

`benchmark/C1Benchmark`. Each of 7 Enron mailboxes is one project. It writes counts and scores only, and the results are in `docs/benchmark/results/c1-baseline-enron.md`.

## Results

10,206 unique messages, 8,215 threads, 914 queries. The figures in brackets are 95% bootstrap intervals.

| Metric | C1 (Lucene) | Recency baseline |
| --- | --- | --- |
| Recall@5 | 0.290 (0.263-0.316) | 0.486 (0.455-0.517) |
| Recall@10 | 0.338 (0.312-0.365) | 0.601 (0.572-0.632) |
| MRR | 0.358 (0.331-0.386) | 0.456 (0.428-0.484) |
| nDCG@10 | 0.306 (0.282-0.330) | 0.459 (0.434-0.486) |

| Isolation check | Count |
| --- | --- |
| Cross-project retrieval probes | 1,828 |
| Thread history probes | 2,314 |
| Thread identifiers shared with the next mailbox | 5,342 |
| **Violations** | **0** |

| Cost | Value |
| --- | --- |
| Retrieval latency p50 / p95 | 2.3 ms / 8.9 ms |
| Index build | 3.6 s |
| Index size on disk | 12 MB |
| Peak process memory, including Maven's JVM | 605 MB |

## The measure

No human relevance labels exist at this scale, so relevance is a **proxy**. The query is an email's new text, with no subject. A result counts as relevant if it is an earlier message in the same thread. Later messages and the email itself are removed before scoring.

- **Recency baseline:** the 10 most recent earlier messages in the mailbox, ignoring content.
- **Thread only:** not scored. `retrieveThread` finds thread messages by identifier, and thread membership defines relevance, so its recall would be 1.0 by construction and say nothing.

## How to read the result

The proxy probably favours recency, and it tests something domain search is not for:

- **Threads are short and close in time.** Earlier thread messages are often just the latest messages in the mailbox, so recency finds them without reading anything.
- **The system already gets thread messages exactly.** `retrieveThread` covers that job, so domain search is for what lies outside the thread, which this proxy does not score.
- **Replies may not share words with earlier messages,** because the stripper removes quoted text and the subject is left out of the query.

These are my explanations, and they are not measured. It is equally possible that BM25 over raw email text is simply weak here. The result does not show that domain retrieval is useful, and it does not show that it is useless.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Report the unfavourable result without tuning | Tuning to a weak proxy would produce a better number with no real meaning | Adjusting query terms until C1 won |
| One mailbox as one project | Gives real boundaries for isolation tests | One big project: nothing to isolate |
| Number threads within each mailbox | Enron has per-file Message-IDs, so natural collisions do not occur. This creates 5,342 equal identifiers on purpose | Original identifiers: the first trial had no collisions, so the test proved nothing |
| Remove duplicates by content | Half the corpus (10,318 messages) was copies in several folders | Message-ID: every copy has its own |
| Index stripped text | Quoted text would make thread matching trivial | Whole bodies |
| Keep subject in documents, not queries | A query with the subject would just rediscover the subject-based threader | Subject in queries: circular |
| Bootstrap intervals, fixed seed | The plan asks for them, and they repeat | Point estimates |

## Problems encountered

1. **False violations in the first trial.** With one mailbox, the "other project" was the same project, and 2,970 violations appeared. The fault was in the benchmark, so cross-project probes now only run between different projects.
2. **Duplicates went undetected.** The first trial reported 0 duplicates, which looked wrong. Enron assigns a different Message-ID to each filed copy, so the benchmark now compares sender, date, subject and body.
3. **A first run failed with a class-not-found error,** because `exec:java` does not compile test classes. `mvn test-compile` must run first.

## Critical points

- **Threading defines relevance here,** and Enron relies on the subject fallback. Over-merged threads such as those with generic subjects count as relevant, whether or not they truly are.
- **The zero-violation result proves only what was probed:** source permissions, project filters and thread scope on these queries. Forged-sender tests belong to step 4.9, and the Phase 4 gate must repeat this with topic agents.
- **The first 7 mailboxes alphabetically are not a random sample of Enron.** No university mail is involved, so domain conclusions do not carry over.
- **`supplemental` retrieval (30 results) was used for all queries,** and future messages are removed afterwards, so fewer than 30 may remain to rank. Normal production queries return only 10.
- **Queries used the whole new text,** capped at 256 terms, rather than chosen keywords. A real query from `MetadataAgent` keywords would behave differently, and that is untested.
- **The default limit of 10 and the budget were not evaluated.** The budget was effectively unlimited here.
- **English only.** The French analyzer has only a small unit test behind it.
- **What C3 must beat.** Step 4.10 compares against this. Use the same proxy and data for that comparison, and add a labelled set if possible, since this proxy may reward the wrong behaviour.
