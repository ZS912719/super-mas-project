# C3 Against C1 on Enron

Quality uses two proxies, not human relevance labels. See `docs/reports/step-4.10.md`.

| Item | Value |
| --- | --- |
| Projects (mailboxes) | 7 |
| Unique messages indexed | 10206 |
| Declared subject topics, from folder names | 14 |
| Thread-proxy queries | 914 |
| Folder-proxy queries | 405 |
| Maximum subject agents per email | 3 |
| Agents run per email, C3 | 1.03 |
| Evidence entries per email before merging, C3 | 5.14 |
| Documents per email after merging, C3 | 5.05 |
| Duplicate rate before merging, C3 | 0.017 |
| Duplicate rate after merging | 0 by construction |
| C3 agents and merge latency p50 | 0.4 ms |
| C3 agents and merge latency p95 | 4.4 ms |

## Thread proxy: earlier messages in the same thread

Thread only and every C3 variant containing the discussion agent find these by identifier, so their score here is high by construction.

| Variant | Recall@5 | Recall@10 | MRR | nDCG@10 |
| --- | --- | --- | --- | --- |
| C1 | 0.290 (0.263-0.316) | 0.338 (0.312-0.365) | 0.358 (0.331-0.386) | 0.306 (0.282-0.330) |
| Recency | 0.486 (0.455-0.517) | 0.601 (0.572-0.632) | 0.456 (0.428-0.484) | 0.459 (0.434-0.486) |
| C3 subject only | 0.003 (0.000-0.007) | 0.003 (0.000-0.007) | 0.004 (0.000-0.008) | 0.003 (0.000-0.007) |
| C3 | 0.947 (0.936-0.956) | 0.988 (0.983-0.992) | 0.993 (0.988-0.997) | 0.994 (0.990-0.997) |
| C3 + correspondent (simulated) | 0.841 (0.822-0.859) | 0.962 (0.953-0.970) | 0.808 (0.790-0.828) | 0.834 (0.820-0.847) |
| Thread only | 0.949 (0.940-0.959) | 0.988 (0.983-0.992) | 1.000 (1.000-1.000) | 1.000 (1.000-1.000) |

## Folder proxy: earlier messages in the same declared folder, in other threads

Results from the email's own thread are removed before scoring, so no variant benefits from thread identifiers.

| Variant | Recall@5 | Recall@10 | MRR | nDCG@10 |
| --- | --- | --- | --- | --- |
| C1 | 0.082 (0.070-0.096) | 0.113 (0.099-0.127) | 0.687 (0.645-0.727) | 0.454 (0.420-0.488) |
| Recency | 0.114 (0.096-0.134) | 0.185 (0.164-0.209) | 0.596 (0.555-0.632) | 0.447 (0.412-0.480) |
| C3 subject only | 0.019 (0.011-0.029) | 0.020 (0.012-0.031) | 0.121 (0.090-0.152) | 0.068 (0.049-0.089) |
| C3 | 0.019 (0.011-0.029) | 0.020 (0.012-0.031) | 0.121 (0.090-0.152) | 0.068 (0.049-0.089) |
| C3 + correspondent (simulated) | 0.057 (0.046-0.070) | 0.065 (0.053-0.078) | 0.544 (0.497-0.591) | 0.278 (0.248-0.309) |

## Paired differences from C1, with 95% bootstrap intervals

| Proxy | Variant | Recall@10 | nDCG@10 |
| --- | --- | --- | --- |
| Thread | C3 subject only | -0.335 (-0.362 to -0.308) | -0.303 (-0.327 to -0.278) |
| Thread | C3 | +0.650 (+0.623 to +0.677) | +0.688 (+0.663 to +0.713) |
| Thread | C3 + correspondent (simulated) | +0.624 (+0.596 to +0.652) | +0.528 (+0.504 to +0.554) |
| Folder | C3 subject only | -0.093 (-0.104 to -0.082) | -0.386 (-0.418 to -0.353) |
| Folder | C3 | -0.093 (-0.104 to -0.082) | -0.386 (-0.418 to -0.353) |
| Folder | C3 + correspondent (simulated) | -0.048 (-0.058 to -0.037) | -0.176 (-0.203 to -0.147) |

## Isolation

Zero violations mean something only if retrieval returns results, which the scores above show.

| Check | Value |
| --- | --- |
| Cross-project probes | 2742 |
| Forged-sender probes | 914 |
| **Violations** | 0 |
