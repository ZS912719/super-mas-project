# Step 4.10: C3 Against C1

## Outcome

**C1 wins the only comparison that is not decided by construction.** On the folder proxy, C3 without correspondent scores Recall@10 0.020 against C1's 0.113. C3 wins on the thread proxy, but only because its discussion agent finds thread messages by identifier, and that proxy defines relevance by thread. So this run gives no evidence that subject topic agents improve retrieval.

The Phase 4 gate is met, because it asks for zero isolation violations and a written result, "including if C1 wins". There were 0 violations.

The full results are in `docs/benchmark/results/c3-vs-c1-enron.md`.

## Results

10,206 messages from 7 mailboxes, as in step 3.5. Brackets are 95% bootstrap intervals.

**Folder proxy (405 queries):** relevant means an earlier message in a declared folder shared with the query, in another thread. Results from the query's own thread are removed before scoring.

| Variant | Recall@10 | MRR | nDCG@10 |
| --- | --- | --- | --- |
| C1 | 0.113 (0.099-0.127) | 0.687 (0.645-0.727) | 0.454 (0.420-0.488) |
| Recency | 0.185 (0.164-0.209) | 0.596 (0.555-0.632) | 0.447 (0.412-0.480) |
| C3 (subject + discussion) | 0.020 (0.012-0.031) | 0.121 (0.090-0.152) | 0.068 (0.049-0.089) |
| C3 + correspondent, **simulated verification** | 0.065 (0.053-0.078) | 0.544 (0.497-0.591) | 0.278 (0.248-0.309) |

**Thread proxy (914 queries):** the proxy from step 3.5. Scores for variants with a discussion agent are high by construction.

| Variant | Recall@10 | MRR | nDCG@10 |
| --- | --- | --- | --- |
| C1 | 0.338 (0.312-0.365) | 0.358 (0.331-0.386) | 0.306 (0.282-0.330) |
| Recency | 0.601 (0.572-0.632) | 0.456 (0.428-0.484) | 0.459 (0.434-0.486) |
| Thread only | 0.988 (0.983-0.992) | 1.000 | 1.000 |
| C3 subject only | 0.003 (0.000-0.007) | 0.004 (0.000-0.008) | 0.003 (0.000-0.007) |
| C3 | 0.988 (0.983-0.992) | 0.993 (0.988-0.997) | 0.994 (0.990-0.997) |
| C3 + correspondent, simulated | 0.962 (0.953-0.970) | 0.808 (0.790-0.828) | 0.834 (0.820-0.847) |

| Isolation check | Count |
| --- | --- |
| Cross-project probes | 2,742 |
| Forged-sender probes | 914 |
| **Violations** | **0** |

| Cost, C3 | Value |
| --- | --- |
| Agents run per email, on thread-proxy queries | 1.03 |
| Evidence entries before merging, per email | 5.14 |
| Documents after merging, per email | 5.05 |
| Duplicate rate before merging | 0.017 |
| Agents and merge latency p50 / p95 | 0.4 ms / 4.4 ms |
| Largest process, the whole benchmark with six variants | 559 MB |
| Whole benchmark | 51 s |

## How to read the result

- **Subject agents hardly ran.** 1.03 agents per email, with the discussion agent always running, means a subject agent ran on about 3% of thread-proxy queries. Keyword activation from 14 folder-name topics rarely fires, so low recall may mostly be low activation. This is my reading of the figures: I did not record activation on the folder queries or test a different activation rule.
- **The fair baseline was not run.** C1 in the design includes thread history through `retrieveThread`, while the C1 column here is domain search alone. "C1 plus thread history" would likely match C3 on the thread proxy. So the +0.650 for C3 there should not be read as an advantage over the intended C1 design.
- **Overlap barely exists in this data.** A duplicate rate of 0.017 means the merge step had little to do, so this run tests it only lightly.
- **The correspondent result is not a product result.** Enron has no verification signal, so every sender was treated as verified. It also lowers thread-proxy ranking (MRR 0.808 against 0.993), which shows that a broad topic can push better results down.
- **Recency beats C1 on Recall@10 on both proxies,** though not on MRR on the folder proxy. Recency cannot see content, so these proxies still reward closeness in time.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Settings fixed before the first run and left unchanged after seeing results | Tuning to these proxies would produce a better number with no real meaning | Lowering the activation threshold or adding keywords until C3 improved |
| A second, folder-based proxy | The thread proxy decides thread-aware variants by construction. Human filing is the only independent signal available | Thread proxy alone: C3 would have appeared to win by 0.65 |
| Topics tagged by keyword, while relevance uses real folder membership | Tagging by folder would make the proxy circular | Using folder membership as the tag |
| The same query input for every variant: new text, no subject | Replies share a subject with their thread, so giving it only to C3 would favour C3 | Using the full analysis in C3 |
| All variants run through real agents, the board and the merge | Measures what the product would do, including rights checks | Calling the index directly |
| C1 must reproduce step 3.5 | A control for the benchmark itself | Trusting the new code |

## Problems encountered

**The first run returned all zeros, with "0 violations".** The retriever copies its sources when built, and I passed the map while it was still empty, so every request was correctly denied. A result of zero violations can therefore come from a system that returns nothing. I fixed the order and added a note in the report saying that the isolation count depends on retrieval working. After the fix, the C1 column matched step 3.5 on all four metrics, and that is the evidence that this run is sound.

## Critical points

- **Declared topics were taken from folder names, which also define the folder proxy.** This shared origin should help C3, yet C3 still lost, so the direction of the conclusion looks safe. The amount of the loss should not be relied on.
- **Folder filing is a weak stand-in for relevance.** Folders may be broad, partial or filed for reasons unrelated to content. A message outside a folder can still be relevant.
- **Only 14 topics exist across 7 mailboxes, and they are not real declared topics.** A person writing topics with good keywords might do better, which this run does not test.
- **Queries for the two proxies differ,** so scores should not be compared between the two tables.
- **The generic-folder list was written by hand,** without being checked against the data beyond the rule. Some kept folders may be generic.
- **Only one run and one seed.** Intervals cover query sampling only. They do not cover the choice of mailboxes, which are the first 7 alphabetically and not a random sample.
- **Missing-information metrics are not measured.** Section 6 of the plan lists precision and recall, plus rules against LLM. No gold data or model exists, so this run does not exercise that check at all.
- **Topic assignment F1 from section 6 is not measured,** because no topic labels exist.
- **No human relevance labels were used.** The decision on a labelled set is still open. Without it, C3 cannot be fairly judged on the job it was designed for.
- **Latency does not include LLM or coordination costs,** and it falls in a range where other differences dominate.

## What this means for the design

I would not treat C3's subject agents as justified by this evidence. The plan's question of whether a topic agent is more than a saved query is still unanswered, with the available evidence leaning towards "not yet". The discussion agent and the merge are useful as structure, but a plain `retrieveThread` plus C1 might do the same job. Keeping C1 plus thread history as the default, with C3 subject agents optional until labelled data says otherwise, is the conclusion I draw.
