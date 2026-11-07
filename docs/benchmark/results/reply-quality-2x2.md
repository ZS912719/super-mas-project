# Reply Quality: the 2×2 (Runs A-D)

Step 13, 2026-11-07. Method: [`docs/benchmark/reply-quality.md`](../reply-quality.md); A and B in detail: [`reply-quality-A-B.md`](reply-quality-A-B.md). All four runs online through OpenRouter (development stand-in), blackboard architecture, threshold 0.4, dev once and multi twice, scored with **revision 2** of the checks (A and B re-scored from their saved replies). System: A/B at `13354df`, C/D at `aed8d59` (the six fixes of step 13 §5e). **Development results:** the fixes were designed from A and B on these 36 emails, and the checks are AI-written and not yet human-reviewed.

| Replies expected (34 per run) | A: 3 B, before | B: 8 B, before | C: 3 B, after | D: 8 B, after |
| --- | --- | --- | --- | --- |
| Drafted | 29 | 34 | **34** | 32 |
| Facts mentioned | 6 / 32 | **13 / 32** | 4 / 32 | 9 / 32 |
| Asked for what is missing | 1 / 10 | 6 / 10 | 4 / 10 | **8 / 10** |
| Greeting right (of drafted) | 25 / 29 | 25 / 34 | **27 / 34** | 20 / 32 |
| Without any violation (of drafted) | 5 / 29 | 1 / 34 | **25 / 34** | 18 / 32 |
| Other emails' content in replies (phrase check, all 48 runs) | 24 | 20 | **0** | **0** |
| Lost to a hung request | 5 | 0 | 0 | 0 |
| Lost to the echo guard (all French) | 1 | 3 | 0 | 6 |
| Signatures / placeholders removed by the guards | — / 10 | — / 1 | 29 / 18 | 33 / 6 |
| Slowest email | 301 s | 17 s | 1.9 s | 13.2 s |

The phrase check looks for 23 distinctive phrases of the other emails (a colleague's room swap, a student number, an NDA, ...) in each reply; three hits in C were "Mon**da**y" matching "nda" and are left out.

## Reading

**System (A→C, B→D).** The fixes removed every case of other people's mail in a reply (24 and 20 → 0), every lease timeout, and most rule violations (5 → 25 and 1 → 18 replies without violation, mostly through the signature and placeholder guards). Asking for what is missing rose (1 → 4, 6 → 8 of 10), as fix 4c intended (missing information read from the knowledge, e.g. the medical certificate).

**Model (A→B, C→D).** The 8 B model again states more facts (13 vs 6, 9 vs 4) and asks more (6 vs 1, 8 vs 4) at about three times the latency; the 3 B model follows the form better after the fixes (more correct greetings, fewer violations).

**What got worse.** Facts fell in both models (6 → 4, 13 → 9). For the emails checked by hand the fact was in the excerpts and the model left it out ("at most seven days" in en-01 and en-m01; "faculty page" in en-m04, where D instead claimed "the exam date has not been changed"), so the relevance filter is not the cause there. A likely cause is the new composer rule "use only the facts that help answer a request", which may make the models terser; this is a hypothesis, not tested. Where a fact is missing for every run (registrar and three working days in en-m11, research office in en-m02), the routing never asks the specialist whose knowledge holds it (only the student or teacher contributes), which the fixes did not touch.

**The echo failures are the model's.** With the guard limited to real prompt labels (fix 3), D still produced six French replies that contain a prompt label such as `<<EMAIL TEXT>>`. The explanation given after run B (French quotation marks typed as `<<`/`>>`) was therefore wrong for at least these: the 8 B model copies parts of its input when the email is French. Failed replies are not recorded, so the exact output is not available.

## Seen in the drafts, not counted by the checks

- The 8 B model greeted the recipient ("Dear Prof. Martin") in en-01 again; the 3 B model wrote as the student to her supervisor ("Dear Laura Fischer", en-10).
- In one D draft (en-10) the model wrote only a closing and a name; after the signature guard the draft is just "Best regards,", which is not a reply and should be refused.
- A plan can list a missing item twice in two forms ("COURSE" from the role's fields and "course (required by student-faq)" from the knowledge).
- Wrong confirmations in other words than the forbidden phrases ("The exam date has not been changed").

## A repeat: how much is noise

Two runs meant as the composer-prompt variant ran by mistake in the main project at `46bed76` (all six prompt versions equal to C's; the variant's composer version would differ). They are therefore a **repeat of C and D** with the same prompts, plus the two small fixes of `46bed76` (greeting-only drafts refused, a missing field listed once). Folders: `runtime/reply-benchmark/C-repeat-3b-46bed76`, `D-repeat-8b-46bed76`.

| Replies expected | C | C repeat | D | D repeat |
| --- | --- | --- | --- | --- |
| Drafted | 34 | 34 | 32 | 32 |
| Facts mentioned | 4 / 32 | 7 / 32 | 9 / 32 | 11 / 32 |
| Asked for | 4 / 10 | 6 / 10 | 8 / 10 | 8 / 10 |
| Without violation | 25 / 34 | 29 / 34 | 18 / 32 | 18 / 32 |

The same configuration differs by 2-3 facts between runs on this set. The fall in facts from A/B to C/D (6 → 4, 13 → 9) is within that spread, so it is **not evidence** that the "use only the facts" rule made the models terser. The variant run (branch `ablation/composer-only-facts`) is still to be done.

