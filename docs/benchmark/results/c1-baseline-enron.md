# C1 Baseline on Enron

Retrieval quality uses a proxy, not human relevance labels. See `docs/reports/step-3.5.md`.

| Item | Value |
| --- | --- |
| Projects (mailboxes) | 7 |
| Unique messages indexed | 10206 |
| Threads, with subject fallback | 8215 |
| Duplicates removed | 10318 |
| Unparseable or undated, skipped | 0 |
| Queries | 914 |
| Load, strip and thread | 7 s |
| Index build | 3.6 s |
| Retrieval latency p50 | 2.3 ms |
| Retrieval latency p95 | 8.9 ms |

## Retrieval, with 95% bootstrap intervals

| Metric | C1 (Lucene) | Recency baseline |
| --- | --- | --- |
| Recall@5 | 0.290 (0.263-0.316) | 0.486 (0.455-0.517) |
| Recall@10 | 0.338 (0.312-0.365) | 0.601 (0.572-0.632) |
| MRR | 0.358 (0.331-0.386) | 0.456 (0.428-0.484) |
| nDCG@10 | 0.306 (0.282-0.330) | 0.459 (0.434-0.486) |

## Isolation

| Check | Value |
| --- | --- |
| Cross-project retrieval probes | 1828 |
| Thread history probes | 2314 |
| Thread identifiers shared with the next mailbox | 5342 |
| **Violations** | 0 |
