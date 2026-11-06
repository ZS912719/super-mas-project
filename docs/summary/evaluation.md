# Benchmarks, Tests and Results

**Results:** `docs/benchmark/results/`. **Data:** `benchmark/data/` (synthetic sets in Git; Enron and personal mail ignored). **Reports:** steps 0.5, 1.10, 3.5, 4.10, 9.8, 10.4, 12.

## Test suites

| Suite | Count (2026-11-07) | Covers |
| --- | --- | --- |
| Java (`mvn test`) | 325, 0 failures | Metadata, retrieval, isolation, memory, topics, roles, prompts, blackboard storage (teammate's 22), promotion, leased coordinator, Phase 12 collaboration and revision, agent service, local-model adapter, end to end on memory and SQLite |
| Mailbox (`npm test`) | 14, 0 failures | Teammate's 11 plus the agent integration |
| Formal (teammate) | nuXmv models of task and projection protocols | `scripts/check-formal.ps1` |

## Benchmarks

| Benchmark | Data | Key result | Caveat |
| --- | --- | --- | --- |
| Facts | 24 synthetic emails | Sender, recipients, date, `currentText`, language F1 1.000; threading 24/24 | Clean synthetic mail |
| Metadata | Enron dev, 100 | `currentText` 0.780; GLiNER PERSON 0.83; speech acts rules 0.54 vs model 0.26 macro F1 | LLM-drafted labels, unreviewed |
| Retrieval C1 / C3 / discovered | Enron, 10,206 messages | Folder proxy Recall@10: C1 0.113, discovered 0.070, declared 0.020; C1 + thread best overall; 0 isolation violations | Proxies, not human relevance |
| Specialists (router) | 24 synthetic | 15/24, never the wrong specialist; 24/24 with gold role hint; 0/72 injection violations | Small set |
| **Router vs blackboard** | 24 single-intent + 12 multi-request | Single-intent 15 = 15; multi-request exact set 4 → 5 (0.4) / 10 (0.2), recall 0.48 → 0.57 / 0.90; requests split right 11/12; ≈2× rounds and writes | Multi-request labels written by the assistant, frozen before the run, unreviewed; threshold chosen before the data |

## What the experiments show

1. **Specialization works where the signal is clear,** and safely: a wrong specialist was never chosen; uncertainty goes to the fallback.
2. **Collaboration helps on mixed emails** at no cost on single-intent ones, but its benefit depends on the contribution threshold.
3. **Plain search plus thread history** is the strongest retrieval measured; topic agents add explainability more than recall.
4. **The rule-based role-hint classifier** is the main routing bottleneck.

## Not yet measured (statement §15)

Relevance of replies, influence of the system prompt and limits of the local model all need a real model; French; human-reviewed labels; a held-out set for the threshold. See [to-do](../todo.md).
