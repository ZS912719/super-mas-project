# Benchmark Protocol

Every benchmark run of the project, in one place, as the basis for the final report. Started 2026-11-11, with all earlier runs backfilled from `docs/benchmark/results/`, the step reports and the Git-ignored `runtime/reply-benchmark/` folders. From now on every run gets its row and its entry as soon as it finishes, including failed and invalid runs.

What is planned, and the ID each run belongs to (C01-C19, P1-P8, W1-W8), is in [`PLAN.md`](PLAN.md).

This file links to the detailed results and does not replace them: each number has one source, named in the entry. Where a run lives only in `runtime/` (ignored by Git), the entry carries its headline numbers.

## Status values

| Status | Meaning |
| --- | --- |
| `valid` | Can be reported as it stands, with the caveats in its entry |
| `development` | Online model (OpenRouter stand-in) or self-authored data or checks; must be re-run locally or on reviewed data before it counts (to-do 2.6, 3.10) |
| `provisional` | Gold labels written by an LLM and not reviewed by a person |
| `proxy` | No relevance labels; quality measured through a proxy defined in the entry |
| `stale` | The code it measures changed afterwards; re-run before reporting |
| `superseded` | Replaced by a later entry (for example re-scored with newer checks) |
| `invalid` | Did not measure what it was meant to; kept with the reason |

## Index (newest first)

| # | Date | Benchmark | Part | Data | Configuration | Commit | Headline | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [B61](#b61) | 2026-11-11 | W2 Single agent vs the system, complete | Whole system | synthetic 24 + 12 | Online 3 B, 8 B, 3 runs each | `14ea40e` (r1-r2) / `83044eb` (r3, same code) | 3 B: more facts (+0.16); 8 B: drafts more (+0.11) and greets right more often (+0.27) than the system | `development` |
| [B60](#b60) | 2026-11-11 | W3 Ablations, complete | Whole system | synthetic 24 + 12 | Online 3 B, 8 B, 3 runs each | `14ea40e` / `83044eb` (same code) | Without knowledge fewer facts (3 B −0.12, 8 B −0.22); neutral prompt (3 B) worse greeting; router: no difference shown | `development` |
| [B59](#b59) | 2026-11-11 | W1 Main result, complete | Whole system | synthetic 24 + 12 | Online 3 B, 8 B, 3 runs each | `14ea40e` / `83044eb` (same code) | 3 B facts 0.21, clean 0.75; 8 B facts 0.30, clean 0.53; 8 B − 3 B: facts n.s., clean −0.22, greeting −0.21, drafted −0.11 | `development` |
| [B58](#b58) | 2026-11-11 | Retry of the missing main runs, credit still insufficient | Whole system | — | Online | `3a7f6f0` | 48 of 48 requests rejected (HTTP 402) in `M-base-3b-r3`; stopped, no run kept | `invalid` |
| [B57](#b57) | 2026-11-11 | P2 Input budget on real and synthetic mail | Context / generation | Enron 100 + 20 longest, synthetic 36 | Fake local model | `5dd7bd5` + server | 0 requests over budget; largest exactly 12,000 (truncation); 3 of the 20 longest emails refused: the plan alone exceeds the budget | `valid` |
| [B56](#b56) | 2026-11-11 | W2 Single agent vs the system | Whole system | synthetic 24 + 12 | Online 3 B (1 run), 8 B (1 run) | `14ea40e` | 3 B: more facts (+0.18), fewer clean replies (−0.26); 8 B: no difference shown | `superseded` (B61) |
| [B55](#b55) | 2026-11-11 | W3 Ablations: knowledge, composer prompt, router | Whole system | synthetic 24 + 12 | Online 3 B (2 runs), 8 B (1 run) | `14ea40e` | Without knowledge: fewer facts (8 B −0.26); router (3 B): more facts, more rule violations | `superseded` (B60) |
| [B54](#b54) | 2026-11-11 | W1 Main result on the reviewed checks | Whole system | synthetic 24 + 12 | Online 3 B (2 runs), 8 B (1 run) | `14ea40e` | 3 B: facts 0.20, clean 0.81, drafted 1.00; 8 B: facts 0.36, clean 0.66; 8 B − 3 B facts +0.15 (0.01 to 0.33) | `superseded` (B59) |
| [B53](#b53) | 2026-11-11 | Main runs cut short by exhausted credit | Whole system | — | Online | `14ea40e` | 8 runs invalid (HTTP 402), 3 B r3 and 8 B r2-r3 missing | `invalid` |
| [B52](#b52) | 2026-11-11 | Human review of the 36 reply checks | Generation (gold) | synthetic 24 + 12 | Owner, label page | `a233d83` | 36 of 36 confirmed unchanged | `valid` (one reader) |
| [B51](#b51) | 2026-11-11 | W9 Replies on real mail, 3 B and 8 B | Whole system | Enron test 100 | Online 3 B / 8 B | `b78d2f9` | Drafted 100 / 95; sender greeted 84 / 64; verified problems: 1 invented confirmation, 2 replies with university knowledge, 0 leaks | `development` |
| [B50](#b50) | 2026-11-11 | P7 Revision after feedback, 3 B and 8 B | Review loop | synthetic 22 | Online 3 B / 8 B | `b78d2f9` | Fact feedback applied 3/9 (3 B), 5/7 (8 B); "shorter" 7/12, 4/12 | `development` |
| [B49](#b49) | 2026-11-11 | C13 Request extraction, LLM, 3 B and 8 B | Specialists | synthetic-multi 12 | Online 3 B / 8 B, 3 repeats | `b78d2f9` | Count exact 12/12 in all 6 runs (rules 11/12); fell back to rules 4-5 (3 B), 1-2 (8 B) of 12 | `development` |
| [B48](#b48) | 2026-11-11 | W9 Replies on real mail, template floor | Whole system | Enron test 100 | Template | `76828c9` + harness | 100/100 drafted, all by the generic agent; greeting names the sender 0/100 | `valid` (floor) |
| [B47](#b47) | 2026-11-11 | C02 Threading: reply headers vs subject fallback | Metadata | synthetic-dev 24 | Parser; `EmailThreader` | `cdce62a` + harness | Headers 22/24, subject fallback 24/24; the fallback is not used by the system | `development` |
| [B46](#b46) | 2026-11-11 | C01 Parser robustness on all Enron mail | Metadata | Enron maildir, 517,401 | Parser + stripper + language | `cdce62a` + harness | 100 % parsed; 0 replies recognised (no reply headers in Enron); p50 0.04 ms | `valid` |
| [B45](#b45) | 2026-11-11 | C14 Conversation memory after a restart | Memory | synthetic-dev 24 (+ leak trap) | Template | `c438594` + harness | Earlier messages found 4/8 (all misses: replies without reply headers); 0 cross-project leaks | `development` |
| [B44](#b44) | 2026-11-11 | P7 Revision after feedback, template floor | Review loop | synthetic 22 | Template | `d69430c` + harness | 22/22 revised, feedback applied 0 (template ignores feedback) | `valid` (floor) |
| [B43](#b43) | 2026-11-11 | P6 Composer input is deterministic (derived check) | Generation | runs C-repeat, C-repeat2 | — | `46bed76` | 48 of 48 composer requests identical across two runs | `valid` |
| [B42](#b42) | 2026-11-11 | W1 Paired comparisons of runs A-D (derived, no new run) | Generation | B15-B24 | — | `5117196` + script | 8 B vs 3 B: facts and asks not significant; clean and greeting significantly worse | `development` |
| [B41](#b41) | 2026-11-11 | C13 Request extraction, rules | Specialists | synthetic-multi 12 | Rules | `a75f33a` + harness | Count exact 11/12; 22 of 23 requests | `development` |
| [B40](#b40) | 2026-11-11 | C10 Missing information in the reply plans | Context / specialists | synthetic 24 + 12 | Rules + knowledge requirements, template | `596152d` + harness | Gold asks found 3/6; over-asking 5 of 17 emails (COURSE) | `development` |
| [B39](#b39) | 2026-11-11 | C09 Topic agents and discovered topics, re-run | Retrieval | Enron, 7 mailboxes | Lucene | `2b8bba3` + working tree | Identical to B10 | `proxy` |
| [B38](#b38) | 2026-11-11 | C08 C1 baseline, re-run | Retrieval | Enron, 7 mailboxes | Lucene | `2b8bba3` + working tree | Identical to B08 | `proxy` |
| [B37](#b37) | 2026-11-11 | P5 Router vs blackboard, re-run | Control | synthetic 24 + 12 | Template, rules metadata | `2b8bba3` + working tree | Identical to B13 in every quality measure | `valid` |
| [B36](#b36) | 2026-11-11 | C11 Excerpt relevance filter | Specialists | synthetic 22 × 6 docs | Rules (`ExcerptRelevance`) | `2b8bba3` + working tree | English: gold facts kept 11/12, other docs dropped 71/90 | `development` |
| [B35](#b35) | 2026-11-11 | C17 Output guards on saved replies | Generation | 6 runs C/D (288 replies) | Guards of `aed8d59`/`46bed76` | — | Placeholders 87 removed, 1 left; signatures 172 removed, 1 left; 16 echo refusals (8 B) | `development` |
| [B34](#b34) | 2026-11-11 | C07 Role-hint rules | Metadata | synthetic-dev 24 | Rules | `2b8bba3` + working tree | Precision 1.000, recall 0.357 (5 of 14) | `development` |
| [B33](#b33) | 2026-11-11 | P1 MetadataAgent on the frozen test set | Metadata | Enron test 100 / hand 25 | Rules vs + GLiNER vs + ONNX acts | `2b8bba3` + working tree | Macro F1 0.617 / 0.663 / 0.659 (hand 25: 0.647 / 0.691 / 0.709) | `valid` |
| [B32](#b32) | 2026-11-11 | C06 Speech acts on the frozen test set | Metadata | Enron test 100 / hand 25 | Rules vs ONNX | `2b8bba3` + working tree | Pooled F1 rules 0.516 (hand 0.293), ONNX 0.471 (hand 0.474) | `valid` |
| [B31](#b31) | 2026-11-11 | C05 Entities on the frozen test set | Metadata | Enron test 100 / hand 25 | Rules, GLiNER, hybrid | `2b8bba3` + working tree | Hybrid PERSON 0.670, DATE 0.443, ID 0.105 | `valid` |
| [B30](#b30) | 2026-11-11 | C03/C04 Facts, stripper and language on the frozen test set | Metadata | Enron test 100 / hand 25 | Rules | `2b8bba3` + working tree | currentText F1 0.567 (hand 0.500; dev was 0.780); language 1.000 | `valid` |
| [B29](#b29) | 2026-11-11 | Review of the LLM test labels (redo) and test-set freeze | Metadata (labels) | Enron test, 48 of 75 + 6 | Owner, label page with checklist | `2b8bba3` + label page | 54 reviewed, 5 changed; test set frozen (25 human, 54 reviewed, 21 LLM) | `valid` (see caveats) |
| [B28](#b28) | 2026-11-11 | Review of the 75 LLM test labels | Metadata (labels) | Enron test, 75 | Owner, label page (test review) | `2b8bba3` + label page | 74 of 75 accepted unchanged; median 1 s between saves | `invalid` |
| [B27](#b27) | 2026-11-11 | Audit agreement, human vs LLM labels | Metadata (labels) | Enron audit, 25 | Hand labels vs LLM drafts | `2b8bba3` + label page | currentText 0.68 exact, entity F1 0.56, κ deliver 0.13, request 0.50 | `valid` (small n) |
| [B26](#b26) | 2026-11-11 | Human labels: Enron audit | Metadata (labels) | Enron audit, 25 | Owner, blind, label page | — | 25 of 25 labelled, 0 validator findings | `valid` |
| [B25](#b25) | 2026-11-11 | Noise of reply quality (derived, no new run) | Generation | B20-B24 | — | — | Same config differs by up to 4 facts and 5 asks | `development` |
| [B24](#b24) | 2026-11-07 | Reply quality D-repeat2 | Generation | synthetic 24 + 12×2 | Online 8 B | `46bed76` | Facts 10/32, asked 3/10, clean 21/31 | `development` |
| [B23](#b23) | 2026-11-07 | Reply quality C-repeat2 | Generation | synthetic 24 + 12×2 | Online 3 B | `46bed76` | Facts 8/32, asked 4/10, clean 30/34 | `development` |
| [B22](#b22) | 2026-11-07 | Reply quality D-repeat | Generation | synthetic 24 + 12×2 | Online 8 B | `46bed76` | Facts 11/32, asked 8/10, clean 18/32 | `development` |
| [B21](#b21) | 2026-11-07 | Reply quality C-repeat | Generation | synthetic 24 + 12×2 | Online 3 B | `46bed76` | Facts 7/32, asked 6/10, clean 29/34 | `development` |
| [B20](#b20) | 2026-11-07 | Reply quality D (8 B, after fixes) | Generation | synthetic 24 + 12×2 | Online 8 B | `aed8d59` | Facts 9/32, asked 8/10, clean 18/32 | `development` |
| [B19](#b19) | 2026-11-07 | Reply quality C (3 B, after fixes) | Generation | synthetic 24 + 12×2 | Online 3 B | `aed8d59` | Facts 4/32, asked 4/10, clean 25/34 | `development` |
| [B18](#b18) | 2026-11-07 | Reply quality B re-scored (checks rev. 2) | Generation | B16's replies | No model | `aed8d59` | Facts 13/32, asked 6/10, clean 1/34 | `development` |
| [B17](#b17) | 2026-11-07 | Reply quality A re-scored (checks rev. 2) | Generation | B15's replies | No model | `aed8d59` | Facts 6/32, asked 1/10, clean 5/29 | `development` |
| [B16](#b16) | 2026-11-07 | Reply quality B (8 B, before fixes) | Generation | synthetic 24 + 12×2 | Online 8 B | `13354df` | Drafted 34/34, facts 13/32 | `superseded` by B18 |
| [B15](#b15) | 2026-11-07 | Reply quality A (3 B, before fixes) | Generation | synthetic 24 + 12×2 | Online 3 B | `13354df` | Drafted 29/34, 5 hung requests | `superseded` by B17 |
| [B14](#b14) | 2026-11-07 | Reply quality template floor | Generation | synthetic 24 + 12 | Template, no model | `13354df` | 0 replies without violation | `valid` |
| [B13](#b13) | 2026-11-07 | Router vs blackboard, threshold sweep | Control | synthetic 24 + 12 | Template, rules metadata | `259567b` | Multi exact 4 vs 5 of 12; requests covered 11/12 | `superseded` by B37 (same result) |
| [B12](#b12) | 2026-11-06 | Prototype end to end, two runs | Whole system | synthetic-dev 24 | Template | `9727574` | 24/24 drafts in both runs | `valid` |
| [B11](#b11) | 2026-11-03 | Routing, plans and injection suite | Specialists | synthetic-dev 24 | Rules routing | `6a38179` | Routing 15/24; 72 injections, 0 violations | `development` |
| [B10](#b10) | 2026-11-03 | Discovered topics vs declared vs C1 | Retrieval | Enron, 7 mailboxes | Lucene | `5c219f1` | Discovered −0.043 R@10 vs C1; C1+history 0.988 | `proxy` |
| [B09](#b09) | 2026-10-31 | C3 vs C1 | Retrieval | Enron, 7 mailboxes | Lucene | `fb4aeef` | Folder proxy R@10: C3 0.020 vs C1 0.113 | `proxy` |
| [B08](#b08) | 2026-10-31 | C1 baseline | Retrieval | Enron, 7 mailboxes | Lucene | `d495d71` | R@10 0.338 vs recency 0.601 | `proxy` |
| [B07](#b07) | 2026-10-31 | Phase 1 metadata gate | Metadata | Enron dev 100 | Rules vs recommended | `b46d9b9` | Macro F1 0.670 vs 0.817 | `provisional` |
| [B06](#b06) | 2026-10-31 | Speech-act and role rules | Metadata (labels) | Enron dev 100 | Rules | `65a89bf` | Acts pooled F1 0.620; macro 0.540 | `provisional` |
| [B05](#b05) | 2026-10-31 | Speech-act models (cross-validation) | Metadata (labels) | Enron dev 91 | Head-only vs SetFit | `b868784` | Macro F1 0.260 vs 0.257 | `provisional` |
| [B04](#b04) | 2026-10-31 | Entity extraction variants | Metadata (entities) | Enron dev 100 | Rules, GLiNER, hybrid | `2e6741d` | Hybrid: PERSON 0.826, DATE 0.727, ID 0.688 | `provisional` |
| [B03](#b03) | 2026-10-31 | Stripper on real mail | Metadata (facts) | Enron dev 100 | Rules | `2301cc0` | Exact currentText 57 → 80 of 100 | `provisional` |
| [B02](#b02) | 2026-10-31 | Facts | Metadata (facts) | synthetic 24 + 12 | Rules | `65f0d55` | All facts 1.000 except threadId 0.917 | `development` |
| [B01](#b01) | 2026-10-31 | Empty predictor (floor) | Metadata | synthetic 24 + 12 | Predicts nothing | `c7fbd0a` | Every field 0 | `valid` |

## Entry template

```
### Bnn
**<Benchmark name>** · <date> · status `<status>`
- Question: what the run answers (to-do item, if any)
- Data: set, size, SHA-256 if known · Configuration: model and where it ran, settings · Commit
- Command: how to reproduce
- Result: headline numbers
- Details: results file or run folder; step report
- Caveats: what limits the result
```

## Entries

### B37
**P5: router vs blackboard with the threshold sweep, re-run at the current commit** · 2026-11-11 · status `valid`
- Question: B13 was stale after `289718f` (participation eligibility) and the generation merge `5504b71`; does it still hold?
- Data and configuration as B13. Commit `2b8bba3` + working tree (the uncommitted changes add only a benchmark option and do not change behaviour).
- Command: `java -cp … org.supermas.prototype.BlackboardBenchmark benchmark/data/synthetic-dev benchmark/data/synthetic-multi docs/benchmark/results/blackboard-vs-router-2026-11-11.md runtime/bench-2026-11-11/bb`
- Result: **every quality measure identical to B13**: single-intent 15/24 for both; multi exact set router 4/12, blackboard 5/12; precision 0.83 / 0.92; recall 0.48 / 0.57; requests answered 11/12; threshold sweep unchanged (0.2 best on multi, 10/12). Median time per email 168 / 332 ms (B13: 64 / 178 ms), not comparable: other jobs ran on the machine at the same time.
- Details: `docs/benchmark/results/blackboard-vs-router-2026-11-11.md`.
- Caveats: as B13: template generation, threshold read on the same 12 emails (to-do 3.6).

### B38
**C08: C1 retrieval baseline, re-run** · 2026-11-11 · status `proxy`
- Command: `java -cp … org.supermas.benchmark.C1Benchmark benchmark/data/enron-raw/maildir 10000 200 docs/benchmark/results/c1-baseline-enron-2026-11-11.md runtime/bench-2026-11-11/c1-index`. Commit `2b8bba3` + working tree.
- Result: identical to B08 in every quality measure (R@10 0.338 vs recency 0.601, MRR 0.358); retrieval p50 2.8 ms (B08: 2.3 ms).
- Details: `docs/benchmark/results/c1-baseline-enron-2026-11-11.md`.

### B61
**W2: single agent against the system, all three runs** · 2026-11-11 · status `development`
- Question and configuration as B56; now three runs per model (3 B r2 and r3, 8 B r2 and r3 added after the credit was restored, B58). Supersedes B56.
- Result (single agent − system, 22 reply-expected emails, 95 % bootstrap interval; bold: excludes 0):

| Model | Facts | Asks | Drafted | Clean | Greeting |
| --- | --- | --- | --- | --- | --- |
| 3 B | **+0.16 (+0.02 to +0.34)** | +0.14 (−0.22 to +0.50) | −0.08 (−0.20 to 0.00) | −0.16 (−0.34 to +0.01) | −0.02 (−0.17 to +0.13) |
| 8 B | −0.04 (−0.23 to +0.13) | −0.17 (−0.44 to +0.06) | **+0.11 (+0.04 to +0.21)** | +0.12 (−0.10 to +0.34) | **+0.27 (+0.10 to +0.43)** |

- Failures and violations (reply-expected drafts of three runs): 3 B single agent 97 drafts, 5 refused for echoing the prompt, wrong greeting 10, decides for the owner 7, wrong language 8; 8 B single agent 102 drafts, no failure, wrong greeting 15, wrong language 13. The 8 B system: 92 drafts, 10 failures (5 echoed prompt blocks, all French: fr-05, fr-10, fr-12; 5 greeting-only replies, all multi-request: en-m04, en-m06, en-m10), wrong greeting 30, of which 11 greet the mailbox owner ("Prof./Dr. Martin").
- Reading: with the 3 B model the single agent states more facts and the system keeps the form better (clean, interval just reaching 0). With the 8 B model the system shows no advantage: the single agent drafts every email and greets the right person significantly more often. The B56 result for 8 B ("no difference") was a single run.
- Details: `runtime/reply-benchmark/M-single-*`, `M-base-*`.

### B60
**W3: ablations, all three runs** · 2026-11-11 · status `development`
- Question and configuration as B55, three runs per configuration and model. Supersedes B55.
- Result (variant − system; bold: interval excludes 0):

| Variant | 3 B facts | 3 B clean | 3 B greeting | 8 B facts | 8 B clean | other intervals excluding 0 |
| --- | --- | --- | --- | --- | --- | --- |
| Without knowledge | **−0.12 (−0.25 to −0.01)** | −0.05 (−0.17 to +0.08) | −0.02 (−0.11 to +0.06) | **−0.22 (−0.44 to −0.05)** | +0.05 (−0.10 to +0.19) | — |
| Neutral composer prompt | +0.10 (−0.05 to +0.26) | −0.17 (−0.36 to +0.02) | **−0.17 (−0.33 to −0.01)** | −0.03 (−0.13 to +0.06) | +0.08 (−0.11 to +0.27) | — |
| Without "only the facts" | +0.05 (−0.01 to +0.13) | +0.02 (−0.05 to +0.10) | **+0.09 (+0.04 to +0.15)** | +0.12 (0.00 to +0.25) | +0.08 (−0.05 to +0.20) | 8 B drafted **+0.08 (+0.02 to +0.15)** |
| Router instead of blackboard | +0.16 (−0.05 to +0.38) | −0.13 (−0.32 to +0.06) | −0.08 (−0.27 to +0.10) | −0.04 (−0.24 to +0.13) | +0.08 (−0.09 to +0.24) | — |

  Violations behind "clean" (3 B, reply-expected drafts of three runs; wrong greeting / decides or confirms for the owner / unrelated knowledge ("noise")): system 102 drafts, 16 / 2 / 5; without knowledge 101, 16 / 6 / 0; neutral prompt 98, 28 / 9 / 0; without "only the facts" 102, 8 / 3 / 7; router 102, 24 / 12 / 6. The 8 B router lost 9 drafts to the generation limit, all on French emails.
- Reading: removing the knowledge lowers the facts for both models, as it must (a check of the benchmark). The composer's instructions protect the 3 B greeting and nothing measurable for 8 B. The router's apparent fact gain for 3 B in B55 (two runs) did not hold with three; it still takes more decisions for the owner (12 vs 2). Removing the "only the facts" sentence did no harm and improved two measures slightly; that it helps is not supported.
- Multiple comparisons: of the 50 intervals (5 variants incl. the single agent of B61, 2 models, 5 measures) 8 exclude 0, against about 2.5 expected by chance; single significant results are indications.
- Noise: run-to-run spread is large (clean share of the system 0.85 / 0.82 / 0.68 for 3 B, 0.68 / 0.44 / 0.53 for 8 B, per generation); later runs were not consistently worse (7 of 12 configuration-model pairs lower, 5 higher), so no shift between the sessions is visible.
- Details: `runtime/reply-benchmark/M-*` (Git-ignored).

### B59
**W1: main result, all three runs** · 2026-11-11 · status `development`
- Question and configuration as B54, three runs per model (B58 explains the gap; the added runs used the same code and options, `src/main`, `benchmark/` and the harness unchanged since `14ea40e`). Supersedes B54. The hosting provider chosen by OpenRouter is not recorded and may differ between requests.

| Model | Facts (21) | Asks (6) | Drafted | Clean | Greeting right |
| --- | --- | --- | --- | --- | --- |
| 3 B (3 runs) | 0.21 | 0.36 | 1.00 | 0.75 | 0.80 |
| 8 B (3 runs) | 0.30 | 0.61 | 0.89 | 0.53 | 0.58 |

  8 B − 3 B: facts +0.09 (−0.08 to +0.28), asks +0.25 (0.00 to +0.56), drafted **−0.11 (−0.21 to −0.04)**, clean **−0.22 (−0.40 to −0.04)**, greeting **−0.21 (−0.39 to −0.03)**.
- Reading: the B54 fact advantage of 8 B (one run) is not confirmed; the 3 B model is significantly better on drafted, clean and greeting, as in B42 on the earlier code. The 8 B failures are echoed prompts on French emails and greeting-only replies on multi-request emails (B61).
- Details: `runtime/reply-benchmark/M-base-*`.

### B58
**Retry of the missing main runs: credit still insufficient** · 2026-11-11 · status `invalid`
- What happened: after a reported credit top-up, the 19 missing main runs of B53 were started at 18:17 (same code as B54-B56: no change to `src/main`, `benchmark/` or the harness since `14ea40e`; same options). Beforehand a request with `max_tokens` 5 succeeded for both models. The first 3 B run (`M-base-3b-r3`) had all 48 requests rejected with HTTP 402 ("You requested up to about 116,000 tokens, but can only afford 68,940"): the system sends no `max_tokens`, so the service reserves the model's whole context against the balance. The account's credit endpoint reported `total_credits` 0. The runner's stop check (any connection error in `scores.jsonl`) ended the 3 B stream; the 8 B stream was stopped by hand in its first run.
- Set aside (renamed `…-INVALID-credit-402-retry`): `M-base-3b-r3`, the unfinished `M-base-8b-r2`.
- Note: the hosting provider chosen by OpenRouter is not recorded in any run (earlier ones included); the test request was served by Cloudflare.
- Consequence: unchanged from B53; the missing repeats wait for credit on the account the key belongs to. Setting `max_tokens` would let requests pass on a small balance, but it changes the requests and so the measured system; not done.

### B57
**P2: the model input never exceeds the budget** · 2026-11-11 · status `valid`
- Question: is the invariant "budgets: total input limit enforced, current email text not exempt" (CLAUDE.md) kept on real mail, including very long messages?
- Method: the system (blackboard, university knowledge) answered each email through its local-model adapter talking to a fake OpenAI-compatible server on 127.0.0.1 (`scripts/benchmark/fake_llm_server.py`, new), which returns one fixed reply and logs the size of each request (sizes only). The adapter refuses over-budget requests before sending, so they would appear as budget failures. No real model, no email leaves the machine. Harness: `RealMailBenchmark` with `--llm-provider local --llm-endpoint http://127.0.0.1:8788 --llm-model fake-budget-probe`.
- Data: Enron frozen test (100), `synthetic-dev` (24), `synthetic-multi` (12), and a stress set of the 20 longest messages of the Enron maildir (226-400 KB; Git-ignored copies in `runtime/p2/longest20`).
- Result: 156 emails; **no request over the budget** of 12,000 characters. Ordinary mail stays well below it (largest 9,692 on Enron, median about 4,100). On the 20 longest messages the email text is cut so that the request is **exactly 12,000** characters at most (17 requests); **3 emails were refused** with "the system prompt and the plan exceed the input budget of composer", because the reply plan, which is never cut, does not fit beside the system prompt.
- Reading: the budget holds without exception, and truncation applies to the email text, not to the trusted prompt or the plan. On extreme inputs the system fails visibly instead of sending a cut plan; why the plan grows that large on these messages (many quoted requests or facts) was not examined.
- Details: `docs/benchmark/results/input-budget-2026-11-11.md`; logs `runtime/p2/` (Git-ignored).

### B56
**W2: single agent vs the multi-agent system** · 2026-11-11 · status `development`
- Question: does the multi-agent system answer better than one model with one generic prompt, given the email and every knowledge document (statement §20)?
- Configuration: `ReplyQualityBenchmark --single-agent benchmark/prompt-variants/single-agent.txt` (`common.txt` plus a one-line generic task, the email and all six documents, no metadata, routing, retrieval or access control; same guards and checks), against the system runs of B54. Valid runs: 3 B r1, 8 B r1 (3 B r2 was cut by the credit error, B53). Commit `14ea40e`.
- Result (difference single − system, share of gold, 95 % bootstrap interval over the 22 reply-expected emails): 3 B facts **+0.18 (+0.01 to +0.38)**, asks −0.13 (−0.46 to +0.17), drafted −0.09 (−0.23 to 0.00), clean **−0.26 (−0.48 to −0.07)**; 8 B facts −0.10 (−0.33 to +0.14), asks −0.33 (−0.92 to +0.08), clean −0.05 (−0.30 to +0.20).
- Reading: with the small model the single agent states more of the gold facts (it sees whole documents) but breaks more rules; with the 8 B model no difference can be shown from one run. Neither direction is established for both models.
- Details: `runtime/reply-benchmark/M-single-*-r1/` (Git-ignored); analysis `tmp/report/make_data.py` → `main_diffs.csv` (local), method `scripts/benchmark/compare_runs.py`.
- Caveats: one run per model; the single agent can read the staff-only marking guide (no access control), but no reply quoted it.

### B55
**W3: ablations (knowledge, composer prompt, architecture)** · 2026-11-11 · status `development`
- Question: what does each part contribute? One change at a time against the system (B54): without knowledge documents (`--knowledge benchmark/knowledge-variants/none`, to-do 3.4); a neutral composer prompt (`--composer-prompt …/composer-neutral.txt`, to-do 3.3); the composer without the "use only the facts" sentence; the step 11 router instead of the blackboard (`--architecture router`, to-do 3.2). Valid runs: 3 B r1 and r2, 8 B r1. Commit `14ea40e`.
- Result (difference variant − system, share of gold, 95 % interval; bold where it excludes 0):

| Variant | 3 B facts | 3 B clean | 8 B facts | 8 B clean |
| --- | --- | --- | --- | --- |
| Without knowledge | −0.11 (−0.24 to 0.00) | −0.02 (−0.14 to +0.11) | **−0.26 (−0.52 to −0.02)** | −0.07 (−0.34 to +0.20) |
| Neutral composer prompt | +0.10 (−0.06 to +0.26) | −0.20 (−0.40 to 0.00) | **−0.12 (−0.24 to −0.02)** | −0.02 (−0.30 to +0.25) |
| Without "only the facts" | +0.02 (−0.02 to +0.08) | −0.03 (−0.10 to +0.03) | +0.05 (−0.14 to +0.24) | −0.11 (−0.36 to +0.14) |
| Router instead of blackboard | **+0.19 (+0.01 to +0.43)** | −0.22 (−0.42 to 0.00) | −0.10 (−0.29 to +0.10) | −0.09 (−0.32 to +0.14) |

  Violations behind "clean" (3 B, drafted reply-expected replies of both runs): system 68 replies with wrong greeting 6, noise 4; router wrong greeting 17, decides for the owner 7; neutral prompt wrong greeting 18, confirms or decides for the owner 6.
- Reading: removing the knowledge lowers the facts stated, as it must if facts come from the knowledge (a check of the benchmark itself); the effect is clear for 8 B and borderline for 3 B. The composer prompt's instructions mainly keep the form: without them the 3 B model greets the wrong person three times as often. The router lets the 3 B specialist state more facts but with more wrong greetings and decisions taken for the owner. The "only the facts" sentence has no measurable effect, which settles the open question of B20 and B25.
- Caveats: two runs (3 B) and one run (8 B) instead of the planned three (B53); intervals touching 0 are not counted as differences.

### B54
**W1: main result on the reviewed reply checks** · 2026-11-11 · status `development`
- Question: how good are the replies of the current system, and how much does the model matter? First runs scored with the checks the owner reviewed (B52), on the current code (generation merge `5504b71`, participation eligibility `289718f` and the fixes of step 13 included).
- Configuration: blackboard, threshold 0.4, OpenRouter 3 B (`meta-llama/llama-3.2-3b-instruct`) and 8 B (`meta-llama/llama-3.1-8b-instruct`), temperature 0.2; each run drafts the 24 dev emails once and the 12 multi emails twice. Valid runs: 3 B r1, r2; 8 B r1 (B53). Commit `14ea40e`.
- Command: `SUPERMAS_LLM_PROVIDER=online SUPERMAS_LLM_MODEL=<model> java -cp … org.supermas.prototype.ReplyQualityBenchmark --label M-base-<3b|8b>-r<n>` (script `main-runs.sh` in the session scratchpad; options per configuration as in B55, B56).
- Result (share of gold over the 22 reply-expected emails, mean over runs and repeats):

| Model | Facts (21) | Asks (6) | Drafted | Clean | Greeting right |
| --- | --- | --- | --- | --- | --- |
| 3 B (2 runs) | 0.20 | 0.38 | 1.00 | 0.81 | 0.86 |
| 8 B (1 run) | 0.36 | 0.58 | 0.91 | 0.66 | 0.70 |

  8 B − 3 B: facts **+0.15 (+0.01 to +0.33)**, asks +0.21 (−0.04 to +0.50), drafted −0.09 (−0.23 to 0.00), clean −0.15 (−0.39 to +0.08), greeting −0.16 (−0.36 to +0.02).
- Reading: consistent with B42 on the earlier runs C/D (3 B clean 0.79, 8 B 0.51 there): the small model follows the form better; the larger states more facts, now with an interval that excludes 0. Facts remain the weak point for both: at most about a third of the gold facts appear in a reply.
- Details: `runtime/reply-benchmark/M-base-*` (Git-ignored).
- Caveats: fewer runs than planned (B53); development set; keyword checks.

### B53
**Main runs cut short: exhausted online credit** · 2026-11-11 · status `invalid` (the runs listed here)
- What happened: during the main runs (W1-W3, 6 configurations × 3 repeats × 2 models, started 13:43) the OpenRouter account ran out of credit at about 14:02. From then on requests were answered with HTTP 402 ("This request requires more credits"), which the system records as failed drafts.
- Invalid and set aside (folders renamed `…-INVALID-credit-402`, kept as evidence): all six 3 B r3 runs (35-48 of 48 requests rejected each), `M-single-3b-r2` (6 of 48 rejected), and the unfinished `M-base-8b-r2`. The 8 B stream was stopped after its first repeat; 8 B r2 and r3 were never run. Every run kept has 0 rejected requests.
- Consequence: B54-B56 rest on two runs (3 B) and one run (8 B) instead of three. To complete them: top up the credit and run the missing repeats with the same commit and options; the excerpt-relevance fix (B51) waits until then, so that all main runs measure the same system.

### B52
**Human review of the 36 frozen reply checks** · 2026-11-11 · status `valid` (one reader)
- Question: are the AI-written reply checks (`reply-gold/v1`, revision 2) right, so that reply-quality results can be reported (to-do 3.10)?
- Method: the owner read each `<email>.reply.json` against its email and the six knowledge documents in the label page's "Reply checks" tab and gave a verdict (ok / changed / unsure), saved as `<email>.reply.review.json` next to the check.
- Result: **36 of 36 "ok"**, no check changed, none marked unsure. Pace: 40 minutes for 36 checks, median 31 s between saves (the 14 no-reply emails need little).
- Consequence: the frozen checks stay as committed in `451a654` and revised in `aed8d59`; earlier results scored with revision 2 (B17-B24, B42) were scored with the reviewed checks. Their remaining limits are those of keyword checks (`reply-quality.md`, "Limits").
- Caveats: one reader; to-do 3.10 also asks for a second reader on a sample.

### B51
**W9: replies on real mail with the 3 B and 8 B models** · 2026-11-11 · status `development`
- Question: on the 100 frozen Enron test emails (no gold answer), are the replies safe and well-formed? One run per model.
- Configuration: OpenRouter `meta-llama/llama-3.2-3b-instruct` and `meta-llama/llama-3.1-8b-instruct`, blackboard, university knowledge; harness and checks as B48 (fixed before these runs). Commit `b78d2f9`. Sends the public Enron test emails to OpenRouter (owner's decision, 2026-11-11).
- Command: `SUPERMAS_LLM_PROVIDER=online SUPERMAS_LLM_MODEL=<model> java -cp … org.supermas.prototype.RealMailBenchmark benchmark/data/enron/labeling/frozen-test docs/benchmark/results/real-mail-<3b|8b>-2026-11-11.md runtime/real-mail/<3b|8b>-2026-11-11` (key from `~/.config/supermas/llm.env`)
- Result (automatic checks):

| Measure | 3 B | 8 B |
| --- | --- | --- |
| Drafted | 100 / 100 | 95 / 100 (5 refused: greeting or closing only) |
| Latency p50 / p95 | 1.0 s / 1.9 s | 2.4 s / 6.4 s |
| Greeting names the sender | **84** / 100 | 64 / 95 |
| Greeting names only a recipient | 1 | 2 |
| Replies without any flag | 76 / 100 | 62 / 95 |
| Promise or claimed action | 1 | 0 |
| University knowledge in a reply | 3 | 1 |
| Another email's content | 2 | 0 |
| Check violations | wrong greeting 3 | wrong greeting 2, wrong language 4, signature 2 |
| Answered by | generic 100 | generic 100 |

- Verification of every serious flag, by reading the drafts (content kept local): **promise** (t0016, 3 B): real, the model invents "The hotel booking is confirmed". **University knowledge**: t0111 (3 B) real, a made-up "university policy on student numbers" in reply to "Here is all the contact info"; t0171 (3 B) real, an email of one character ("9") answered with the office hours and the facilities portal; t0007 (both models) a false alarm, the word "assignment" is in the email's subject, which the check did not exclude. **Another email's content** (t0019, t0034, 3 B): no leak; both replies repeat the sender's own text back, and the same passage is also in another test email of the thread. **Wrong language** (4, 8 B): false alarms; English replies to English emails, the language detector misjudged short or unusual email text (as in B46).
- Reading: verified problems are 1 invented confirmation and 2 replies with irrelevant university knowledge in 195 replies; 0 cross-email leaks. t0171 shows a cause: when the requests have no meaningful word, `ExcerptRelevance` keeps every excerpt ("no stems: keep all"), so all knowledge reaches the composer. The 3 B model greets the sender more often; the 8 B model loses 5 replies to the greeting-only guard. A frequent quality problem the checks do not count: replies that repeat the sender's own text or restate the sender's commitments as the owner's.
- Details: `docs/benchmark/results/real-mail-3b-2026-11-11.md`, `docs/benchmark/results/real-mail-8b-2026-11-11.md`; drafts and the 20-reply rubric sample in the Git-ignored run folders.
- Caveats: one run per model; automatic flags need the verification above; the rubric sample (W6-style, 20 replies) is not scored yet.
- Follow-up (owner's decision, 2026-11-11): fix `ExcerptRelevance` so that requests without meaningful words keep only thread history, not all knowledge; **after** the main reply-quality runs (W1-W4), so those runs measure the current system. Re-run W9 after the fix.

### B50
**P7: revision after feedback with the 3 B and 8 B models** · 2026-11-11 · status `development`
- Configuration: as B44 with OpenRouter 3 B and 8 B; one run each. Commit `b78d2f9`.
- Command: `SUPERMAS_LLM_PROVIDER=online SUPERMAS_LLM_MODEL=<model> java -cp … org.supermas.prototype.RevisionBenchmark docs/benchmark/results/revision-<3b|8b>-2026-11-11.md runtime/llm-runs/p7-<3b|8b>`
- Result:

| Feedback | 3 B: applied | 8 B: applied |
| --- | --- | --- |
| "Please also mention: <fact>" | 3 / 9 | **5 / 7** |
| "Please ask the sender for <item>" | 0 / 1 | — |
| "Please make the reply shorter" | **7 / 12** | 4 / 12 |
| Facts lost, new violations | 0, 0 | 1, 2 |
| v1 or v2 not drafted | 0 | 2 without v1, 1 without v2 |

- Reading: the larger model follows a content request more often; the smaller one shortens more often and never broke anything. With one run per model and 7-12 emails per kind, these are indications, not differences that can be claimed (B25 noise).
- Details: `docs/benchmark/results/revision-3b-2026-11-11.md`, `docs/benchmark/results/revision-8b-2026-11-11.md`.
- Caveats: reply checks not yet reviewed by a person (to-do 3.10); the kind of feedback depends on what v1 missed, so the two models got partly different feedback.

### B49
**C13: request extraction with the LLM, 3 B and 8 B** · 2026-11-11 · status `development`
- Configuration: harness of B41; OpenRouter 3 B and 8 B, 3 repeats each. Commit `b78d2f9`.
- Command: `SUPERMAS_LLM_PROVIDER=online SUPERMAS_LLM_MODEL=<model> java -cp … org.supermas.prototype.RequestExtractionBenchmark docs/benchmark/results/request-extraction-llm-<3b|8b>-2026-11-11.md --repeats 3 --llm-cache runtime/llm-runs/c13-<3b|8b>`
- Result: count exact **12 / 12 in all six runs** (rules: 11 / 12), 23 of 23 requests. The model's answer was rejected (a quote not verbatim in the email, or invalid JSON) and the extractor fell back to the rules in 5, 5, 4 of 12 emails (3 B) and 2, 1, 2 (8 B).
- Reading: with the verbatim-quote check and the rule fallback, the LLM extractor is never worse than the rules and fixes the one email the rules miss; the 8 B model's answers are accepted far more often. The extractor is not wired into the prototype (rules are the default).
- Details: `docs/benchmark/results/request-extraction-llm-3b-2026-11-11.md`, `docs/benchmark/results/request-extraction-llm-8b-2026-11-11.md`.

### B48
**W9: replies on real mail, template floor and wiring check** · 2026-11-11 · status `valid` (floor)
- Question: on real mail with no gold answer, are the replies safe and well-formed? This run checks the harness and gives the floor; the model runs follow (the owner agreed on 2026-11-11 that the public Enron test emails may be sent to the online model).
- Data: the frozen Enron test set (B29), 100 emails, through the system as built for the university mailbox (blackboard, university knowledge). New harness `RealMailBenchmark` (test scope) and test `RealMailBenchmarkTest`. Checks needing no gold, fixed before any model run: greeting names the sender (display name or address words) and never only a recipient; the reply checks with no facts (placeholder, signature, staff-only text, language); a fixed list of promises and claimed actions; a 6-word sequence of another test email; a fixed list of university-knowledge phrases; who answered. Email and reply text only in the Git-ignored run folder (`drafts.md`, `rubric-sample.md` with 20 replies for a person).
- Command: `java -cp … org.supermas.prototype.RealMailBenchmark benchmark/data/enron/labeling/frozen-test docs/benchmark/results/real-mail-template-2026-11-11.md runtime/real-mail/template-2026-11-11` (add `--llm-…` flags or `SUPERMAS_LLM_*` for a model)
- Result: drafted 100 / 100, p50 0.3 s; **answered by the generic agent in all 100**: no specialist fits company mail; greeting names the sender 0 / 100 (the template says "Hello,"); placeholder 39; promises, university knowledge and other emails' content 0.
- Details: `docs/benchmark/results/real-mail-template-2026-11-11.md`.

### B47
**C02: threading, reply headers vs subject fallback** · 2026-11-11 · status `development`
- Question: how many emails get the right thread, with the reply headers the prototype uses on receipt, and with `EmailThreader` with or without its subject fallback?
- Data: synthetic-dev 24, gold `threadId`. New harness `ThreadingBenchmark` (test scope).
- Command: `java -cp … org.supermas.benchmark.ThreadingBenchmark docs/benchmark/results/threading-synthetic-2026-11-11.md benchmark/data/synthetic-dev`
- Result: parser on receipt **22 / 24**; `EmailThreader` references only 22 / 24; **with subject fallback 24 / 24**. The two errors are en-03 and fr-03, replies with "Re:" but no reply headers.
- Reading: `EmailThreader` is used only by the benchmarks; no system code calls it, so the prototype never applies the subject fallback. Together with B45 (the history of en-03 and fr-03 is lost) and B46 (Enron has no reply headers at all), this is the main gap of the memory path: on mail without reply headers, threads are not recognised. Using the threader on receipt would need care: a subject fallback can merge unrelated mail with the same subject (C1 counts 8,215 threads with the fallback for 10,206 Enron messages, B08).
- Details: `docs/benchmark/results/threading-synthetic-2026-11-11.md`.

### B46
**C01: parser robustness on the whole Enron corpus** · 2026-11-11 · status `valid`
- Question: does the parser, followed by the stripper and the language detector, survive real mail?
- Data: every file of the Enron maildir, 517,401 messages in 150 mailboxes. New harness `ParserRobustnessBenchmark` (test scope); counts only.
- Command: `java -Xmx2g -cp … org.supermas.benchmark.ParserRobustnessBenchmark benchmark/data/enron-raw/maildir docs/benchmark/results/parser-robustness-enron-2026-11-11.md`
- Result: **parsed 517,401 (100 %)**, stripper and language detector failures 0; no sender, date or Message-ID missing; no recipients 20,382 (3.9 %); empty current text after stripping 55,564 (10.7 %, mostly forwards and attachments-only mail); **replies recognised by reply headers: 0**; detected French 231 (0.04 %, false positives: the corpus is English). Time per message: parse p50 0.036 ms, p95 0.109 ms, p99 0.299 ms; with stripping and language detection p50 0.087 ms, p95 0.473 ms, p99 1.543 ms. Wall time 183 s.
- Reading: the parser is robust. The Enron corpus carries no `In-Reply-To` or `References` headers, so the prototype's header threading sees every message as a new thread (B47).
- Details: `docs/benchmark/results/parser-robustness-enron-2026-11-11.md`.
- Caveats: whether the 3.9 % without recipients truly have none (e.g. only Bcc) was not checked message by message; on the frozen test set recipients score 1.000 (B30).

### B45
**C14: conversation memory after a restart, and isolation between projects** · 2026-11-11 · status `development`
- Question: is a thread's history found again after the system restarts, and never from another project?
- Method: new harness `MemoryBenchmark` (test scope). Phase 1 processes all 24 `synthetic-dev` emails under a second project with the same thread identifiers (a leak trap). Phase 2 processes them under the real project, each in a freshly created wiring on the same data folder (a restart before every email). The thread messages on each board are compared with the earlier emails of its gold thread (by message id; agents' copies carry a `#…` suffix). Template generation.
- Command: `java -cp … org.supermas.prototype.MemoryBenchmark docs/benchmark/results/memory-restart-2026-11-11.md runtime/bench-2026-11-11/c14`
- Result: 6 emails continue a thread, with 8 earlier messages in all. **Found after the restart: 4 / 8**; from other threads: 0; **from the other project: 0** (no leak). The four found are the direct replies (en-02, en-06, fr-02, fr-06: one earlier message each). The four missed all belong to en-03 and fr-03.
- Reading: en-03 and fr-03 are replies ("Re: …") without `In-Reply-To` or `References` headers, so the parser gives each a new thread: the same two errors as threadId 0.917 in B02 and B34. Memory works whenever threading works (4 / 4); the misses are a threading gap (plan C02: no subject fallback in the prototype's receive path), not a memory one.
- Details: `docs/benchmark/results/memory-restart-2026-11-11.md`.
- Caveats: 8 links only; template generation (retrieval does not depend on the model).

### B44
**P7: revision after feedback, template floor** · 2026-11-11 · status `valid` (floor and wiring check)
- Question: does a revision apply reviewer feedback without breaking what was right? This run checks the harness and gives the floor; the model runs follow.
- Data: the 22 reply-expected synthetic emails. Feedback chosen from the frozen checks: the first gold fact v1 misses ("Please also mention: …"), else the first gold ask it misses, else "Please make the reply shorter"; in the email's language. New harness `RevisionBenchmark` (test scope) and test `RevisionBenchmarkTest`.
- Command: `java -cp … org.supermas.prototype.RevisionBenchmark docs/benchmark/results/revision-template-2026-11-11.md runtime/bench-2026-11-11/p7-template` (add `--llm-…` flags or `SUPERMAS_LLM_*` for a model)
- Result: v1 drafted 22/22, rejected and revised 22/22 (feedback kinds: fact 10, ask 1, shorter 11); feedback applied 0, facts lost 0, new violations 0, as expected: the template ignores feedback.
- Details: `docs/benchmark/results/revision-template-2026-11-11.md`.

### B43
**P6: the composer's input is deterministic (derived check)** · 2026-11-11 · status `valid`
- Question: P6 planned to compare composers on one recorded board state. Is that needed, or is the composer's input already the same in every run?
- Method: the recording of each run names each model request by a hash of the model, system prompt and data. Compared the request sets of C-repeat and C-repeat2 (same commit `46bed76`, same model) for dev-r1, multi-r1 and multi-r2.
- Result: **identical** in all three: 24 + 12 + 12 = 48 of 48 requests, and multi-r1 equals multi-r2. Everything before the composer (metadata, routing, retrieval, plans) is rules and gives the same input every time.
- Consequence: all run-to-run variation of the reply results (B25) comes from the model; a model or composer-prompt comparison (W1, W3) already compares composers on identical input, so P6 needs no harness of its own.

### B42
**W1: paired comparisons of the existing reply-quality runs (derived, no new run)** · 2026-11-11 · status `development`
- Question: which differences between A-D survive the run-to-run noise (B25)?
- Method: new script `scripts/benchmark/compare_runs.py`. For each of the 22 reply-expected emails, each measure is averaged over all runs and repeats of a configuration; the two configurations are compared on the same emails (summed difference, 95 % bootstrap interval over emails, 10,000 resamples, seed 20261111). Totals count each email once (21 gold facts, 6 gold asks), unlike the earlier tables, which counted the two multi repeats separately (32 and 10).
- Commands: `python3 -I scripts/benchmark/compare_runs.py --a <runs> --b <runs> --names … --out docs/benchmark/results/compare-<x>-2026-11-11.md` with C×3 vs D×3, A vs C×3, B vs D×3 (A and B with checks revision 2).
- Result:

| Comparison | Facts (21) | Asks (6) | Drafted (22) | Clean (22) | Greeting right (22) |
| --- | --- | --- | --- | --- | --- |
| 8 B (D×3) − 3 B (C×3) | +1.3 (−3.5 to +6.5) | +1.0 (−0.2 to +2.7) | −2.3 (−5.0 to 0.0) | **−6.2 (−10.7 to −1.7)** | **−6.7 (−10.3 to −3.2)** |
| 3 B: after fixes (C×3) − before (A) | +1.2 (−3.2 to +5.8) | +2.2 (0.0 to +5.2) | **+4.0 (+1.0 to +7.5)** | **+14.3 (+10.2 to +18.0)** | +3.5 (−0.7 to +7.7) |
| 8 B: after fixes (D×3) − before (B) | −2.5 (−6.3 to +0.5) | +0.2 (−1.2 to +1.8) | −2.3 (−5.0 to 0.0) | **+10.7 (+6.7 to +14.7)** | **−4.7 (−8.7 to −1.0)** |

  Bold: the interval excludes 0.
- Reading: the claim of B16-B20 that the 8 B model "states more facts and asks more" is **not supported** once noise is accounted for; what is supported is that it follows the form worse (more replies with a violation, more wrong greetings, fewer drafts because of the echo guard). The system fixes clearly raised clean replies for both models; the fall in facts after the fixes (B20 reading) is not significant either. The 8 B greeting got worse after the fixes, a regression to look into.
- Details: `docs/benchmark/results/compare-C-vs-D-2026-11-11.md`, `docs/benchmark/results/compare-A-vs-C-2026-11-11.md`, `docs/benchmark/results/compare-B-vs-D-2026-11-11.md`.
- Caveats: A and B are single runs, so their side is noisier; 22 emails give wide intervals; checks not yet reviewed by a person (to-do 3.10); online models; the fixes were designed on these emails (to-do 3.6).

### B41
**C13: request extraction, rules** · 2026-11-11 · status `development`
- Question: does the request extractor find as many requests as the gold says? (The LLM extractor is the second variant, still to run with the model.)
- Data: synthetic-multi 12, gold request counts (`multi-gold/v1`, 23 requests). `currentText` from the stripper. New harness `RequestExtractionBenchmark` (test scope), which also runs the LLM extractor when a model is configured.
- Command: `java -cp … org.supermas.prototype.RequestExtractionBenchmark docs/benchmark/results/request-extraction-rules-2026-11-11.md`
- Result: count exact **11 / 12**, too few 1, too many 0; 22 of 23 requests. Agrees with B37, where the blackboard's request splitter (the same rules) gets 11/12.
- Details: `docs/benchmark/results/request-extraction-rules-2026-11-11.md`.
- Caveats: self-authored set; counts only, not whether each quote is the right sentence.

### B40
**C10: missing information in the reply plans** · 2026-11-11 · status `development`
- Question: do the specialists' plans list what the reply must ask the sender for (to-do 3.9), and nothing else?
- Data: synthetic-dev 24 and synthetic-multi 12; gold = the frozen `mustAsk` checks (6 asks in 5 of the 22 reply-expected emails). Prototype: blackboard, template generation, rules-only metadata, missing-information rules plus `KnowledgeRequirements`. New harness `MissingInformationBenchmark` (test scope). Field names are read as words (ID = "student number", COURSE = "course").
- Command: `java -cp … org.supermas.prototype.MissingInformationBenchmark docs/benchmark/results/missing-information-2026-11-11.md runtime/bench-2026-11-11/c10`
- Result: gold asks found **3 / 6** (medical certificate in en-01, en-m01, en-m11). Missed: the medical certificate of the French twin fr-01, the student number in en-m11, and the clarification question of en-m06 (no rule can produce it). **Over-asking:** 5 of 17 reply-expected emails without a gold ask still list an item, every time COURSE (en-10, en-m02, en-m04, en-m07, en-m08); 2 of 14 no-reply emails list one too (en-02 COURSE, en-04 ID).
- Reading: the knowledge requirement works for English mail, not for its French twin (English knowledge, French email: the requirement is not matched). COURSE is a required field of the student and teacher roles, so it is asked for even when the email does not concern a course (a thesis submission, a research question).
- Details: `docs/benchmark/results/missing-information-2026-11-11.md` (per-email table).
- Caveats: 6 gold asks only; checks not yet reviewed by a person (to-do 3.10); the plans are those of template runs, which match model runs because plans are built by rules before generation.

### B39
**C09: topic agents and discovered topics, re-run** · 2026-11-11 · status `proxy`
- Command: `java -cp … org.supermas.benchmark.C3Benchmark benchmark/data/enron-raw/maildir 10000 200 docs/benchmark/results/c3-discovered-enron-2026-11-11.md runtime/bench-2026-11-11/c3-index`. Commit `2b8bba3` + working tree.
- Result: identical to B10 in every quality measure and count (214 discovered topics; discovered −0.043 R@10 against C1; C1 + thread history 0.988). Discovery time 10.5 s (B10: 9.6 s).
- Details: `docs/benchmark/results/c3-discovered-enron-2026-11-11.md`. B09 (C3 vs C1 without discovered topics) is contained in this run's variants and also unchanged.

### B36
**C11: excerpt relevance filter** · 2026-11-11 · status `development`
- Question: does `ExcerptRelevance` keep the knowledge a reply needs and drop unrelated documents?
- Data: 22 reply-expected synthetic emails (dev and multi) × 6 knowledge documents = 132 pairs. Relevant = the document holds a gold fact (`mustMention`, `mustAsk`) of the email; noise = it holds a term of the email's `noise` check. Commit `2b8bba3` + working tree (new harness `ExcerptRelevanceBenchmark`).
- Command: `java -cp … org.supermas.agent.role.ExcerptRelevanceBenchmark src/test/resources/university/knowledge docs/benchmark/results/excerpt-relevance-2026-11-11.md benchmark/data/synthetic-dev benchmark/data/synthetic-multi`
- Result: English emails: relevant documents kept 11/12 (0.92), other documents dropped 71/90 (0.79), noise documents dropped 21/25 (0.84). French emails: relevant kept 4/4, other dropped only 6/26: the documents are English and the filter keeps excerpts in another language than the requests by design.
- Details: `docs/benchmark/results/excerpt-relevance-2026-11-11.md`.
- Caveats: the reply checks are not yet reviewed by a person (to-do 3.10); the filter sees all requests, an upper bound of one specialist's view; 6 one-paragraph documents. C12 (`KnowledgeRequirements`) was not benchmarked: the knowledge holds about two requirement sentences, too few for a number; its unit tests cover it.

### B35
**C17: output guards, measured on saved replies** · 2026-11-11 · status `development`
- Question: how often do the guards act, and what gets past them?
- Data: the saved results of runs C, C-repeat, C-repeat2 (3 B) and D, D-repeat, D-repeat2 (8 B), 288 composed replies (`runtime/reply-benchmark/*/scores.jsonl`). What the guards removed is read from each draft's explanation; what got past them is the reply checks' own placeholder and signature checks (a separate implementation). No model call.
- Result:

| Guard | Acted | Left after the guard |
| --- | --- | --- |
| Trailing placeholder | 87 removed (`[Your Name]` 75, `[Signature]` 9, 3 others) | 1 placeholder (3 B) |
| Trailing signature | 172 removed (owner's name in most; "composer", the agent's own name, 6) | 1 signature (3 B) |
| Prompt echo | 16 replies refused (all 8 B) | — |
| Greeting or closing only | 2 replies refused (8 B) | — |

  Also seen, not a guard: 19 replies of the 8 B model in the wrong language, and 47 wrong greetings (3 B: 14).
- Reading: the placeholder and signature guards catch nearly everything (about 99 %); every removed placeholder was a real one. The echo guard costs the 8 B model 16 of 144 replies (11 %), all from French emails (B20): a model limit the guard turns into a visible failure instead of a leaked prompt.
- Caveats: precision of the signature guard is judged from the removal notes, not by a person reading every case.

### B34
**C07: role-hint rules** · 2026-11-11 · status `development`
- Data: synthetic-dev 24 (14 with a gold role hint), SHA-256 `cbd11d761baf…`. Commit `2b8bba3` + working tree.
- Command: `BenchmarkRunner benchmark/data/synthetic-dev docs/benchmark/results/synthetic-dev-metadata-rules-2026-11-11.md metadata-rules` (and `metadata-all-models`).
- Result: role hint precision 1.000, recall 0.357 (5 of 14), null accuracy 1.000: never wrong when it answers, silent in most cases. Same run: currentText 1.000, threadId 0.917, language 1.000, speech acts F1 0.471 (rules) / 0.519 (ONNX); macro F1 0.698 / 0.716.
- Details: `docs/benchmark/results/synthetic-dev-metadata-*-2026-11-11.md`. `synthetic-multi` cannot run this way: its gold files hold the multi-request schema.
- Caveats: self-authored set; role hints cannot be evaluated on Enron (none in the gold labels).

### B33
**P1: the MetadataAgent on the frozen Enron test set** · 2026-11-11 · status `valid`
- Question: macro F1 of the whole metadata part on unseen real mail, rules-only vs the recommended configuration.
- Data: frozen test set (B29), 100 emails, SHA-256 `2f02ed8f0847…`; hand-labelled 25 alone, SHA-256 `fd8107a30941…`. Folders `benchmark/data/enron/labeling/frozen-test{,-human25}` (Git-ignored copies of the frozen files). Commit `2b8bba3` + working tree.
- Command: `java -cp … org.supermas.benchmark.BenchmarkRunner benchmark/data/enron/labeling/frozen-test docs/benchmark/results/test-<variant>.md <variant>` for `metadata-rules`, `metadata-entity-model`, `metadata-all-models`; the same on `frozen-test-human25` into `test-human25-<variant>.md`.
- Result:

| Variant | Macro F1, 100 | Macro F1, hand 25 | Dev (B07) | p50 per email |
| --- | --- | --- | --- | --- |
| Rules only | 0.617 | 0.647 | 0.670 | 1.4 ms |
| Rules + GLiNER entities (recommended) | **0.663** | 0.691 | 0.817 | 249 ms |
| + ONNX speech acts | 0.659 | **0.709** | — | 283 ms |

- Reading: the recommended configuration still beats rules only, but by less than on dev, and every part scores lower on unseen mail (B30-B32). The ONNX acts help on the hand-labelled 25 and not on all 100.
- Caveats: B29 (two halves labelled differently); macro F1 averages over fields with very different difficulty, including trivially perfect facts.

### B32
**C06: speech acts on the frozen test set** · 2026-11-11 · status `valid`
- Data and command as B33 (`labels-rules`, `metadata-all-models`).
- Result: pooled over the four acts, rules F1 **0.516** on 100 and **0.293** on the hand 25; ONNX classifier **0.471** and **0.474**. Dev (B06): rules 0.620.
- Reading: the rules drop sharply against human labels; the ONNX model is steadier between the two halves. The test set has 77 gold acts in 100 emails.
- Details: `docs/benchmark/results/test-labels-rules.md`, `docs/benchmark/results/test-metadata-all-models.md` and the `test-human25-*` files.
- Caveats: per-act scores are not in the runner's report; `propose` and `commit` have very few positives.

### B31
**C05: entity extraction on the frozen test set** · 2026-11-11 · status `valid`
- Data and command as B33 (`entity-rules`, `entity-gliner`, `entity-hybrid`).
- Result (F1; gold count in 100):

| Type (gold) | Rules | GLiNER | Hybrid | Hybrid, hand 25 | Hybrid on dev (B04) |
| --- | --- | --- | --- | --- | --- |
| PERSON (81) | 0.085 | 0.670 | 0.670 | 0.651 | 0.826 |
| DATE (67) | 0.443 | 0.365 | 0.443 | 0.280 | 0.727 |
| ID (8) | 0.182 | 0.000 | 0.105 | 0.182 | 0.688 |
| DEADLINE (2) | 0 | 0 | 0 | — | 0.476 |
| COURSE (0) | — | — | 5 false positives | — | — |

- Reading: PERSON holds up best. DATE recall is low, especially against the hand labels (0.226 recall on the 25), which label many more dates. DEADLINE and COURSE cannot be evaluated on Enron (2 and 0 gold mentions).
- Details: `docs/benchmark/results/test-entity-*.md`, `docs/benchmark/results/test-human25-entity-*.md`.

### B30
**C03/C04: facts, stripper and language on the frozen test set** · 2026-11-11 · status `valid`
- Data and command as B33 (`facts`).
- Result: sender, recipients, threadId, language 1.000; date 0.990 (one email); **currentText F1 0.567** (53 of 94 exact; hand 25: 0.500); on dev it was 0.780 (B03, B07).
- Reading: the stripper rules were tuned on the dev set (B03 stopped at 80 of 100 there), so the drop on unseen mail is most likely over-fitting to dev's formats; the stricter human labels lower it further. This is the weakest fact and feeds every later step.
- Details: `docs/benchmark/results/test-facts.md`, `docs/benchmark/results/test-human25-facts.md`. `StripperDiff` reads only dev drafts, so the errors are not classified yet.

### B29
**Review of the LLM test labels (redo of B28) and freezing of the Enron test set** · 2026-11-11 · status `valid` (see caveats)
- Question: correct the LLM labels where the audit (B27) found them unreliable, then freeze the 100-email test set.
- Data: the 48 non-audited test emails with something to check (DATE / DEADLINE, possible unlabelled dates, `deliver`, `commit`, `propose`), plus 6 others reviewed along the way. Label page "Enron test review" with the "Needs a look" filter. The first 18 saves were made with the earlier page version (no checklist, because the running server predated the change); the other 36 with the checklist, which blocks saving until every flagged point is confirmed or changed.
- Commands: `python3 -I scripts/labeling/build_prelabels.py review-stats benchmark/data/enron/labeling/audit benchmark/data/enron/labeling/test`, then `… finalize-test … --allow-unreviewed`
- Result: 48 of 48 flagged emails reviewed, 54 in all. 49 unchanged, 5 changed: DATE added 4, DEADLINE added 1, DATE removed 1, `deliver` removed 1, `propose` added 1. Pace: 27 minutes for 54 emails; median 23 s between saves, none under 5 s; checklist saves median 20.5 s spent, every flagged point checked.
- Test set frozen: 100 `labeling/test/*.gold.json` (Git-ignored): 25 human (B26), 54 reviewed, 21 LLM labels (emails with none of the fields the audit found unreliable). SHA-256 over the 100 file hashes: `72e758a402294e3a…` (`sha256sum test/*.gold.json | sha256sum`).
- Caveats: **the two halves of the test set are labelled differently.** Per email, the owner's blind audit labels have DATE 1.24, PERSON 0.56, `deliver` 0.44, `commit` 0; the reviewed or LLM half has DATE 0.48, PERSON 0.89, `deliver` 0.37, `commit` 0.07. The audit (B27) showed the LLM missing many dates the owner labels (20 human-only DATE), but the review added only 5. Likely anchoring on the pre-filled labels (reviewers tend to accept what is shown) and possibly sample differences (25 vs 75 emails). Consequences: report DATE and `commit` results on the test set separately for the 25 human-labelled emails, or with this caveat; and state in the report that 75 labels are LLM labels reviewed by one person, not hand labels.

### B28
**Review of the LLM labels of the 75 non-audited test emails** · 2026-11-11 · status `invalid`
- Question: correct the LLM labels where the audit (B27) found them unreliable (DATE / DEADLINE, `deliver`, `commit`), so `finalize-test` can use reviewed labels.
- Data: the 75 test emails outside the audit, LLM drafts pre-filled in the label page's "Enron test review" tab. The drafts hold 33 DATE, 1 DEADLINE, 29 `deliver`, 5 `commit`, 25 `request`, 1 `propose`.
- Command: `python3 -I scripts/labeling/build_prelabels.py review-stats benchmark/data/enron/labeling/audit benchmark/data/enron/labeling/test`
- Result: 75 of 75 saved; 74 unchanged; 1 changed (one DATE added, `deliver` added). No `deliver`, `commit` or DATE removed and no DATE↔DEADLINE retyped.
- Why invalid: the saves span 13.4 minutes; the median gap between two saves is 1 s, 54 of 74 gaps are under 5 s and 65 under 15 s. That is too fast to read the emails, so most drafts were accepted without a check. It also disagrees with the audit, where on comparable emails the same LLM labelled `deliver` 14 times against the owner's 11 (κ 0.13) and `commit` 5 times against 0. The review files must not be used as human-reviewed labels.
- Files: renamed to `labeling/test/*.gold.reviewed-invalid-b28.json` (Git-ignored) and kept as evidence; `finalize-test` and the label page ignore them.
- Follow-up: the test review tab now lists only the 48 emails with something to check (22 with dates, 7 with possible unlabelled dates, 29 `deliver`, 5 `commit`, 1 `propose`; several reasons can apply to one email), and saving requires every checklist point to be confirmed or changed; each save records the points checked and the seconds spent. The redo will be its own entry.
- Caveats: some emails are genuinely quick (bulk mail, forwards with no text), so a few fast accepts are plausible; 1 s as the typical gap is not.

### B27
**Audit agreement: human vs LLM labels on the Enron audit** · 2026-11-11 · status `valid` (25 emails only)
- Question: are the LLM labels of the 75 unreviewed test emails good enough to use (labelling setup, to-do 3.1)?
- Data: the 25 audit emails (random test emails, seed `20261101`), owner's hand labels (B26) vs the LLM drafts in `labeling/test/`. Commit `2b8bba3`.
- Command: `python3 -I scripts/labeling/build_prelabels.py audit-compare benchmark/data/enron/labeling/audit benchmark/data/enron/labeling/test`
- Result:

| Measure | Value |
| --- | --- |
| `currentText` exact agreement | 0.68 (17 of 25) |
| Entities, mean F1 per email | 0.56 |
| Role hint agreement | 1.00 (all `null` on both sides) |
| κ `request` (human 5, LLM 5) | 0.50 |
| κ `propose` (human 1, LLM 3) | 0.47 |
| κ `deliver` (human 11, LLM 14) | 0.13 |
| κ `commit` (human 0, LLM 5) | 0.00 |

  Breakdown (computed after the comparison, labels unchanged):

| Entity type | Both | Human only | LLM only |
| --- | --- | --- | --- |
| PERSON | 13 | 1 | 4 |
| DATE | 11 | 20 | 11 |
| DEADLINE | 0 | 0 | 5 |
| ID | 1 | 2 | 4 |
| COURSE | 0 | 0 | 2 |

  Totals: human 48 entities, LLM 51; 2 texts found by both with a different type. Of the 8 `currentText` differences, 6 are one text containing the other (a boundary: sign-off, greeting or quoted part), 6 have the human text longer, 2 shorter.
- Reading: PERSON agrees well. DATE is where most disagreement is (31 one-sided), probably span boundaries ("Sunday" vs "Sunday morning") and what counts as a date; the LLM also uses DEADLINE and COURSE where the owner sees none. On speech acts, `deliver` and `commit` are near chance: the LLM labels them much more often. By Landis and Koch's scale, `request` and `propose` agree moderately, `deliver` and `commit` slightly or not at all.
- Decision: **`finalize-test` not run.** The setup (labelling-setup "Limits") says that with low agreement the LLM labels are not reliable until the rules are fixed or more is reviewed by hand. Options for the owner: hand-review the 75 remaining test drafts on DATE / DEADLINE and speech acts; or report test results on the 25 audited emails only, with wide intervals; or sharpen the DATE and `deliver`/`commit` rules in `field-schema.md` (that would apply to future labelling, and the audit labels must stay as they are).
- Caveats: 25 emails; κ is very imprecise at this size, and `commit` has no human positive at all. The audit labels must not be changed after this comparison.

### B26
**Human labels: the Enron audit** · 2026-11-11 · status `valid`
- Question: an independent human reference for the test set (to-do 3.1).
- Data: 25 emails in `benchmark/data/enron/labeling/audit/` (Git-ignored, real mail). Labelled blind by the owner with the labelling page (`scripts/labeling/label_server.py`), written to `<id>.gold.json`.
- Result: 25 of 25 labelled; the page's validator reports 0 errors and 0 warnings on all. Distribution: speech acts deliver 11, request 5, propose 1, commit 0; entities DATE 31, PERSON 14, ID 3; role hint `null` on all 25; 1 empty `currentText` (a pure forward).
- Caveats: one rater; no second reader yet.

### B25
**Noise of reply quality (derived from B19-B24, no new run)** · 2026-11-11 · status `development`
- Question: how much do the reply-quality counts move between identical runs? Needed before reading any difference between configurations.
- Data: three runs per model with the same prompt versions (`0b4b3787ebb0`, …): C, C-repeat, C-repeat2 (3 B) and D, D-repeat, D-repeat2 (8 B). The repeats also carry the two small fixes of `46bed76`.
- Result (replies expected, 34 per run):

| | C | C-repeat | C-repeat2 | D | D-repeat | D-repeat2 |
| --- | --- | --- | --- | --- | --- | --- |
| Drafted | 34 | 34 | 34 | 32 | 32 | 31 |
| Facts mentioned (of 32) | 4 | 7 | 8 | 9 | 11 | 10 |
| Asked for (of 10) | 4 | 6 | 4 | 8 | 8 | 3 |
| Without violation | 25 | 29 | 30 | 18 | 18 | 21 |

- Reading: facts vary by up to 4 of 32, asks by up to 5 of 10, clean replies by up to 5. A difference between two configurations smaller than this is not evidence. Comparisons need several repeats and a paired test per email.
- Caveats: online models; checks not yet reviewed by a person (to-do 3.10).

### B24
**Reply quality D-repeat2 (8 B)** · 2026-11-07 · status `development`
- Question: meant as the composer-prompt ablation (without "use only the facts"), but the variant prompt was **not applied** (prompt versions equal to D's). It therefore counts as a repeat of D. Not documented until this protocol.
- Data: synthetic-dev 24 once, synthetic-multi 12 twice. Configuration: OpenRouter `meta-llama/llama-3.1-8b-instruct`, blackboard, threshold 0.4. Commit `46bed76`. Started under the label `V-8b-no-only-facts`, folder renamed later.
- Result: drafted 31/34, facts 10/32, asked 3/10, without violation 21/31, slowest p95 4.8 s.
- Details: `runtime/reply-benchmark/D-repeat2-8b-46bed76/`.
- Caveats: as B25.

### B23
**Reply quality C-repeat2 (3 B)** · 2026-11-07 · status `development`
- Question: as B24, for the 3 B model (label `V-3b-no-only-facts`); variant not applied, so a repeat of C. Not documented until this protocol.
- Configuration: OpenRouter `meta-llama/llama-3.2-3b-instruct`. Commit `46bed76`.
- Result: drafted 34/34, facts 8/32, asked 4/10, without violation 30/34; one slow email (p95 63 s in the no-reply group).
- Details: `runtime/reply-benchmark/C-repeat2-3b-46bed76/`.

### B22
**Reply quality D-repeat (8 B)** · 2026-11-07 · status `development`
- Question: as B24 (label `D2-8b-no-only-facts`); a repeat of D.
- Result: drafted 32/34, facts 11/32, asked 8/10, without violation 18/32.
- Details: `runtime/reply-benchmark/D-repeat-8b-46bed76/`; `docs/benchmark/results/reply-quality-2x2.md` ("A repeat").

### B21
**Reply quality C-repeat (3 B)** · 2026-11-07 · status `development`
- Question: as B23 (label `C2-3b-no-only-facts`); a repeat of C.
- Result: drafted 34/34, facts 7/32, asked 6/10, without violation 29/34.
- Details: `runtime/reply-benchmark/C-repeat-3b-46bed76/`; `reply-quality-2x2.md`.

### B20
**Reply quality D: 8 B after the six fixes** · 2026-11-07 · status `development`
- Question: the system's effect for the larger model (cell D of the 2×2, to-do 3.2).
- Configuration: OpenRouter `meta-llama/llama-3.1-8b-instruct`, blackboard, threshold 0.4, checks revision 2. Commit `aed8d59`.
- Command: `SUPERMAS_LLM_MODEL=meta-llama/llama-3.1-8b-instruct mvn -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=org.supermas.prototype.ReplyQualityBenchmark -Dexec.args="--label D-8b"` (with the online provider settings of `docs/plans/llm-setup.md`).
- Result: drafted 32/34 (6 French replies lost to the echo guard), facts 9/32, asked 8/10, without violation 18/32, other emails' content 0, slowest 13.2 s.
- Details: `docs/benchmark/results/reply-quality-2x2.md`; `runtime/reply-benchmark/D-8b/`; step 13 §5f.
- Caveats: the fixes were designed from A and B on the same emails (to-do 3.6); online model.

### B19
**Reply quality C: 3 B after the six fixes** · 2026-11-07 · status `development`
- Configuration: OpenRouter `meta-llama/llama-3.2-3b-instruct`; otherwise as B20.
- Result: drafted 34/34, facts 4/32, asked 4/10, without violation 25/34, other emails' content 0, slowest 1.9 s.
- Details: as B20, folder `runtime/reply-benchmark/C-3b/`.

### B18
**Reply quality B re-scored with checks revision 2** · 2026-11-07 · status `development`
- Question: B16's saved replies with the corrected checks (last names accepted in greetings; a signature after the closing is a violation). No model call.
- Command: `… -Dexec.args="--label B-8b-checks2 --rescore runtime/reply-benchmark/B-8b"`
- Result: drafted 34/34, facts 13/32, asked 6/10, without violation 1/34 (the signature check flags nearly every reply).
- Details: `runtime/reply-benchmark/B-8b-checks2/`; `reply-quality-2x2.md` (column B).

### B17
**Reply quality A re-scored with checks revision 2** · 2026-11-07 · status `development`
- As B18 for A. Result: drafted 29/34, facts 6/32, asked 1/10, without violation 5/29.
- Details: `runtime/reply-benchmark/A-3b-checks2/`.

### B16
**Reply quality B: 8 B, system before the fixes** · 2026-11-07 · status `superseded` by B18
- Question: the model's effect (B vs A) with the system as it was.
- Configuration: OpenRouter `meta-llama/llama-3.1-8b-instruct`, blackboard, threshold 0.4, checks of `451a654` (revision 1). Commit `13354df`.
- Result (revision 1): drafted 34/34, facts 13/32, asked 6/10, without violation 12/34; other emails' content in 20 of 48 replies; 3 French replies lost to the echo guard.
- Details: `docs/benchmark/results/reply-quality-A-B.md`; `runtime/reply-benchmark/B-8b/`; step 13 §5d.

### B15
**Reply quality A: 3 B, system before the fixes** · 2026-11-07 · status `superseded` by B17
- Configuration: OpenRouter `meta-llama/llama-3.2-3b-instruct`; otherwise as B16.
- Result (revision 1): drafted 29/34 (5 lost to hung requests, about 300 s each), facts 6/32, asked 1/10, without violation 12/29; other emails' content in 24 of 48 replies.
- Details: as B16, folder `runtime/reply-benchmark/A-3b/`. The run exposed the cross-thread disclosure, the stalled-body timeout and the echo-guard defects fixed in step 13 §5e.

### B14
**Reply quality: template floor** · 2026-11-07 · status `valid`
- Question: the score with no language model (lower bound).
- Configuration: template generation, prompt `f0fc3e8d2cac`. Commit `13354df`.
- Result: dev (10 replies expected): greeting 0, facts 3/10, asked 0/2, without violation 0; multi (12): greeting 1, facts 0/11, asked 0/4, without violation 0. The template fails greeting and placeholder checks by construction.
- Details: `docs/benchmark/reply-quality.md`, "Template floor".

### B13
**Router vs blackboard, with the contribution-threshold sweep** · 2026-11-07 · status `superseded` by B37, which reproduced it
- Question: does the Phase 12 blackboard find the right agents for multi-request mail better than the step 11 router?
- Data: synthetic-dev 24 (gold role from role hint), synthetic-multi 12 (gold role set). Configuration: template generation, rules-only metadata, SQLite board, fresh data per set. Commit `259567b`.
- Command: `BlackboardBenchmark DEV_DIR MULTI_DIR REPORT_FILE WORK_DIR` (test scope, `org.supermas.prototype`).
- Result:

| Measure | Router | Blackboard |
| --- | --- | --- |
| Single-intent: right agent | 15/24 | 15/24 |
| Multi: agent set exact | 4/12 | 5/12 |
| Multi: role precision / recall | 0.83 / 0.48 | 0.92 / 0.57 |
| Multi: every request answered | n/a | 11/12 |
| Median time per email | 64 / 67 ms | 178 / 149 ms |

  Sweep (blackboard): threshold 0.2 gives multi exact 10/12 (precision 1.00, recall 0.90) and single 16/24; the default 0.4 gives 5/12.
- Details: `docs/benchmark/results/blackboard-vs-router.md`; step 12.7.
- Caveats: the threshold is chosen on the same 12 emails (to-do 3.6). Stale: control code changed in `289718f` (participation eligibility) and the generation merge `5504b71`; re-run at the current commit.

### B12
**Prototype end to end, first and second run** · 2026-11-06 · status `valid`
- Question: does `PrototypeApp` process every email, and does a second run on the same data reuse the first run's memory?
- Data: synthetic-dev 24; template generation. Commit `9727574`.
- Result: 24/24 drafts awaiting review in both runs; 0 discovered topics at the start of run 1, 4 at the start of run 2.
- Details: step 11.4.
- Caveats: a functional check more than a quality benchmark.

### B11
**Routing, plans and injection suite of the specialized agents** · 2026-11-03 · status `development`
- Question: do the specialists get the right emails, cite only permitted evidence, and resist prompt injection?
- Data: synthetic-dev 24 (SHA-256 `cbd11d761baf…`), knowledge `src/test/resources/university/knowledge`. Routing `ScoreRoutingStrategy(0.4, 0.1)`. Commit `6a38179`.
- Command: `mvn -q test-compile exec:java -Dexec.mainClass=org.supermas.benchmark.RoleBenchmark -Dexec.args="benchmark/data/synthetic-dev src/test/resources/university/knowledge docs/benchmark/results/roles-synthetic.md"`
- Result: routing right 15/24 (0.63) with predicted role hint + keywords; 12/24 keywords only; 24/24 with the gold role hint (upper bound). Plans 24/24; citations not permitted or not on the board: 0. Injection suite: 72 cases, 0 violations (template generation, so the model was not attacked).
- Details: `docs/benchmark/results/roles-synthetic.md`; step 9.8.
- Caveats: self-authored set; to-do 3.8 runs the injections through the real model.

### B10
**Discovered topics vs declared topics and C1** · 2026-11-03 · status `proxy`
- Question: do automatically discovered topics retrieve better than declared folders or plain search?
- Data: Enron, 7 mailboxes, 10,206 messages; 914 thread-proxy and 405 folder-proxy queries; 214 discovered topics. Commit `5c219f1`.
- Command: `C3Benchmark MAILDIR MIN_MESSAGES QUERIES_PER_PROJECT REPORT_FILE INDEX_DIR`
- Result (Recall@10, 95 % bootstrap intervals in the file): thread proxy: discovered only 0.321, C1 0.338, **C1 + thread history 0.988** (the fair baseline), C3 all 0.917. Paired, discovered topics are −0.043 R@10 against C1 (−0.052 to −0.034). Folder proxy: C1 + history 0.113, C3 all 0.073.
- Details: `docs/benchmark/results/c3-discovered-enron.md`; step 10.4.
- Caveats: proxies, not relevance labels; recency still rewarded.

### B09
**C3 (topic agents) vs C1** · 2026-10-31 · status `proxy`
- Data and command as B10 (without discovered topics). Commit `fb4aeef`.
- Result: folder proxy (the only comparison not decided by construction) R@10 C3 0.020 vs C1 0.113; thread proxy C3 0.988 vs C1 0.338, but only because the discussion agent reads the thread.
- Details: `docs/benchmark/results/c3-vs-c1-enron.md`; step 4.10.

### B08
**C1 retrieval baseline** · 2026-10-31 · status `proxy`
- Question: how well does one Lucene retriever per mailbox find earlier messages of the same thread from the new text?
- Data: Enron, 7 mailboxes, 10,206 unique messages, 914 queries. Commit `d495d71`.
- Command: `C1Benchmark MAILDIR MIN_MESSAGES QUERIES_PER_PROJECT REPORT_FILE INDEX_DIR`
- Result: C1 R@5 0.290, R@10 0.338, MRR 0.358, nDCG@10 0.306; recency baseline R@10 0.601. Isolation: 0 violations.
- Details: `docs/benchmark/results/c1-baseline-enron.md`; step 3.5.

### B07
**Phase 1 metadata gate: rules only vs recommended configuration** · 2026-10-31 · status `provisional`
- Data: Enron dev 100 (unreviewed LLM drafts, SHA-256 `ecccd321…`). Commit `b46d9b9`.
- Command: `BenchmarkRunner DATA_DIRECTORY REPORT_FILE metadata-rules` and `… metadata-entity-model`
- Result: macro F1 0.670 (rules) vs 0.817 (rules + GLiNER entities). Facts: sender, recipients, date, language 1.000; currentText 0.780; threadId trivial (single-message threads).
- Details: `dev-provisional-metadata-rules.md`, `dev-provisional-metadata-entity-model.md`; step 1.10.
- Caveats: B27 shows the LLM labels disagree with a human on DATE and on `deliver`/`commit`, so these dev numbers are measured against a doubtful reference.

### B06
**Speech-act and role-hint rules** · 2026-10-31 · status `provisional`
- Data: Enron dev 100. Commit `65a89bf`. Command: `BenchmarkRunner … labels-rules`.
- Result: speech acts pooled P 0.663, R 0.582, F1 0.620 (on the stripper's output); macro F1 over the four acts 0.540 on labelled text (step 1.8); role hint 1 of 3.
- Details: `dev-provisional-labels-rules.md`; step 1.8.

### B05
**Speech-act models: head-only vs SetFit, 5-fold cross-validation** · 2026-10-31 · status `provisional`
- Data: Enron dev, 91 emails with text. Commit `b868784`. Command: `scripts/training/train_setfit.py cv`.
- Result: macro F1 0.260 (head-only) vs 0.257 (SetFit); deliver 0.63 / 0.62, request 0.41 / 0.30, commit and propose about 0 (6 and 10 positives). Rules win (B06).
- Details: step 1.7.

### B04
**Entity extraction: rules, GLiNER, hybrid** · 2026-10-31 · status `provisional`
- Data: Enron dev 100. Commit `2e6741d`. Command: `BenchmarkRunner … entity-rules | entity-gliner | entity-hybrid`.
- Result (F1, gold count): PERSON (149) rules 0.146, GLiNER 0.826, hybrid 0.826; DATE (51) 0.727 / 0.580 / 0.727; DEADLINE (8) 0.476 / 0.533 / 0.476; ID (20) 0.462 / 0.385 / 0.688; COURSE (2) 0 / 1.000 / 1.000. GLiNER p50 about 250 ms per email, rules about 1 ms.
- Details: `dev-provisional-entity-*.md`; step 1.6.

### B03
**Stripper rules on real mail** · 2026-10-31 · status `provisional`
- Data: Enron dev 100. Commit `2301cc0`. Tool: `StripperDiff`.
- Result: exact `currentText` matches 57 → 80 of 100; synthetic set stays at F1 1.000.
- Details: step 1.3b.

### B02
**Facts benchmark** · 2026-10-31 · status `development`
- Data: synthetic-dev 24 (+ multi 12), SHA-256 `cbd11d761baf…`. Commit `65f0d55`. Command: `BenchmarkRunner benchmark/data/synthetic-dev REPORT facts`.
- Result: sender, recipients, date, currentText, language 1.000; threadId 0.917; macro F1 0.455 only because entity and label fields are left empty by this predictor.
- Details: `docs/benchmark/results/facts-synthetic.md`; step 1.4.
- Caveats: self-authored data.

### B01
**Empty predictor** · 2026-10-31 · status `valid`
- The floor for the metadata runner: every field 0 (speech acts and role hint count only true negatives). Commit `c7fbd0a`. Details: `docs/benchmark/results/baseline-empty.md`.
