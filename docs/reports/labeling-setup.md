# Labelling Setup: Enron Dev and Test Sets

## What was built

- `scripts/labeling/sample_enron.py` selects a sample across all mailboxes and splits it, printing counts only.
- `scripts/labeling/build_prelabels.py` turns LLM pre-labels into draft gold files, checks them against each email, and promotes reviewed drafts.
- `ParsedMessage` now reads addresses leniently, with a new test.
- `docs/benchmark/field-schema.md` gained the rules below, so reviewed and hand labels follow the same conventions.

Everything containing mail is in `benchmark/data/enron/labeling/`, which Git ignores.

| Folder | Emails | State |
| --- | --- | --- |
| `dev/` | 100 | LLM drafts in `.gold.draft.json`, none reviewed |
| `test/` | 100 | LLM drafts in `.gold.draft.json`; no model has run on these emails. **Do not open these drafts before finishing the audit** |
| `audit/` | 25 | Random test emails (seed `20261101`, chosen before any were read), unlabelled, for hand labelling |

The earlier import in `benchmark/data/enron/` was left in place but is not used.

## Why

**A new sample.** The first import came from one mailbox: 86 of 120 emails had the same sender. Results from it would not generalise. The new sample takes at most 2 emails from each of the 150 mailboxes (seed `20261031`), with 88 to 93 distinct senders per split.

**Sampling rules, fixed before any email was read.** Excluded: no `From` header, the `no.address@enron.com` placeholder, and bodies under 40 or over 6,000 characters. Only one email per normalized subject is kept, so a conversation cannot cross the split. Bulk and automated mail is kept because a real inbox holds it.

**Provenance of drafts.** `currentText` is cut from the raw body between two anchors, so it cannot contain invented text. Every entity and act quote must occur in it, or the build fails.

## How to label

**Dev set (review):**
1. Open each `dev/*.gold.draft.json` with a non-empty `_attention` list (32 of 100) next to its `.eml`.
2. Correct any field, then set `_reviewed` to `true`.
3. Run `python3 -I scripts/labeling/build_prelabels.py promote benchmark/data/enron/labeling/dev`. It writes `.gold.json` for the 68 unflagged drafts and for flagged drafts marked as reviewed.

The 68 unflagged drafts get no human check. Do not rerun `build` after starting the review, because it overwrites the drafts.

**Audit (by hand, without seeing the LLM's labels):**
1. For each `audit/*.gold.todo.json`, read the `.eml`, check the facts, and fill in the empty fields using `docs/benchmark/field-schema.md`.
2. Remove the `_status` line and rename the file to `.gold.json`.
3. Run these two commands. The second writes the frozen test set, using your label on the audited emails:

```bash
python3 -I scripts/labeling/build_prelabels.py audit-compare benchmark/data/enron/labeling/audit benchmark/data/enron/labeling/test
python3 -I scripts/labeling/build_prelabels.py finalize-test benchmark/data/enron/labeling/audit benchmark/data/enron/labeling/test
```

Do not change the audit labels after seeing the comparison, or it stops being independent. Do not run any model on the test set until `finalize-test` is done.

## Results

| Measure | Dev drafts | Test drafts |
| --- | --- | --- |
| Drafts passing every check | 100 of 100 | 100 of 100 |
| Flagged for attention | 32 | 31 |
| Empty `currentText` | 9 | 6 |
| Speech acts | deliver 47, request 28, commit 10, propose 6; none on 27 emails | deliver 43, request 30, commit 10, propose 4; none on 31 emails |
| Entities | PERSON 149, DATE 51, ID 20, DEADLINE 8, COURSE 2 | PERSON 84, DATE 55, ID 10, DEADLINE 6, COURSE 2 |
| Role hints | administration 2, student 1 | none |

The facts benchmark on an unreviewed copy of the dev drafts gave:

| Field | F1 |
| --- | --- |
| sender, recipients, date, threadId | 1.000 |
| language | 0.990 |
| **currentText** | **0.581** |

Treat these as provisional until the review is done. `threadId` is trivial here because the sample has only single-email threads, so it says nothing about threading.

## What went wrong

**An importer bug corrupted facts.** Jakarta Mail rejects a whole header if one address is malformed, such as `a..martin@enron.com`, even in non-strict mode. As a result 11 of 13 "no recipients" dev drafts and both "no sender" drafts were wrong, and recipients scored F1 0.656 only because the reference was wrong. The first fix, non-strict parsing, did not work, and the new test caught that. Addresses are now found with a pattern. Facts on both splits were regenerated, and all 200 `.eml` files were confirmed byte-identical. Any import made before this fix has the same fault.

## Limits

- **The Phase 1 facts gate is not met on real mail.** `currentText` scored 1.000 on our synthetic set but about 0.58 here, so the earlier "gate is met" holds for synthetic data only. The stripper needs work on Enron forward and reply formats. The dev set may be used for this; the test set may not.
- **Significance is not guaranteed.** With 100 test emails, a 95% interval on a score near 0.8 is about ±8 points. McNemar's test depends on how many emails two variants disagree on, so small improvements may show no measurable difference.
- **Rare labels cannot be scored reliably.** On the test set, `propose` has 4 emails, DEADLINE 6 mentions and `commit` 10 emails. SetFit may also struggle to learn them from the dev set.
- **COURSE and `roleHint` cannot be evaluated on Enron.** The test set has 2 COURSE mentions and no role hints, because Enron is not university mail. These fields need another source or must be reported as not evaluated.
- **Test scores measure agreement with an LLM's labels on 75 of 100 emails.** Results must be described as LLM-labelled with a human audit, and always with the audit's agreement figures. Those figures come from only 25 emails and will be imprecise, especially for rare labels. If agreement is low, the labels are not reliable enough to use until the rules are fixed or more is reviewed by hand.
- **The same LLM wrote the schema rules and the labels,** so the audit is the only independent check on both. A later LLM checker (Phase 5) would be graded partly against an LLM's labels, which may flatter it, so that comparison is the weakest one. Report it on the 25 hand-labelled emails too.
- **All 200 sampled emails were sent to an online LLM** (Claude) for labelling. The model was Claude Opus 5.5 (`claude-opus-5-5`), in a Claude Code session on 2026-10-31 that wrote `prelabels.json` (dev) at 13:57 UTC and `prelabels-test.json` (test) at 14:13 UTC; established on 2026-11-11 from the session log, which records the model of every message. The plan allows this for public data such as Enron. It must not be done with personal mail.
