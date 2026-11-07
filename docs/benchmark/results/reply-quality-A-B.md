# Reply Quality: Runs A (3 B) and B (8 B)

Step 13, 2026-11-07. Method: [`docs/benchmark/reply-quality.md`](../reply-quality.md). Both runs online through OpenRouter (development stand-in), system as of commit `13354df`, blackboard architecture, threshold 0.4, dev once and multi twice; frozen checks of commit `451a654` (human review pending). Raw outputs (ignored by Git): `runtime/reply-benchmark/A-3b/`, `runtime/reply-benchmark/B-8b/` (`results.md`, `drafts.md`, `scores.jsonl`, recordings).

| | A: `meta-llama/llama-3.2-3b-instruct` | B: `meta-llama/llama-3.1-8b-instruct` |
| --- | --- | --- |
| Replies expected and drafted | 29 / 34 | **34 / 34** |
| Facts mentioned (keyword) | 6 / 32 | **13 / 32** |
| Asked for what is missing (keyword) | 1 / 10 | **6 / 10** |
| Greeting names the sender (re-scored by first *or* last name, see below) | 24 / 29 | 25 / 34 |
| Greets the recipient or a wrong person | 5 | 6, plus 2 invented names ("Yumi", "Yutaka" for Yuki) |
| A name after the closing, against the rule | 24 / 29 | 34 / 34 |
| `[Your Name]`-type placeholders removed by the guard | 10 | 1 |
| Keyword prohibitions hit | 4 (2 accepts a meeting, 1 invented room, 1 student number repeated) | 0 (but see the disclosure below) |
| Lost to a hung request (lease expired after 301 s) | 5 | 0 |
| Lost to the echo guard, all French | 1 | 3 (all "no reply expected") |
| Latency p50 per email | ≈ 1 s | ≈ 2.5-3.3 s |

Figures with "keyword" are the automatic checks; they miss correct paraphrases and they do not see everything wrong (see findings 1 and 6).

## Findings

**System (ours):**

1. **Disclosure across threads (high).** For en-m06 ("Could we talk about it sometime?"), the generic specialist searched the whole project's mail (`#topic:generic.search` on the `mail` source) and passed five other people's emails to the composer: a colleague's room swap, a partner's dataset, a student's illness and extension, the registrar's contract. Both models wrote them into the reply to Chris; B even promised "I will sign the attached contract amendment". The same mechanism put the registrar's enrolment deadline (en-04) into the reply to Omar (en-10, run A). Role agents are readers of the `mail` source (`PrototypeApp.Wiring`), and their search is not limited to the email's own thread or sender. No check caught it: the frozen prohibitions did not foresee other emails' content.
2. **A hung online request outlived the timeout.** Five tasks in A were fenced after the 5-minute lease although the request timeout is 3 minutes. `HttpRequest.timeout` in Java bounds the wait for the response headers only; if the headers arrive and the body stalls, the call waits on. The control trail shows "Lease has expired" for these emails.
3. **The echo guard refuses French replies.** "The model echoed prompt blocks" refused four French drafts; the guard rejects any text with both `<<` and `>>`, and French quotation marks are often typed as `<<`/`>>`. Failed replies are not recorded, so this is the likely, not a proven, cause. *Correction after run D:* with the guard limited to real prompt labels, the 8 B model still produced six such French replies, so for these the model really copied prompt labels; the quotation-mark explanation was wrong for at least those ([2×2 results](reply-quality-2x2.md)). The board then ends `STOPPED` with no failure reason (the composer does not retry the same contributions, step 12 fix D2), so the cause is visible only in the execution records.
4. Earlier diagnosis still holds: unrelated excerpts and the email's own details presented as facts (step 13 §5b).

**Checks (mine):**

5. **The greeting check only accepted first names**, so "Dear Prof. Leroy" or "Dear Mr. Ruiz" counted as wrong. Re-scored by hand with the sender's first or last name in the table above; the frozen files are unchanged until approved.
6. **No check for a name after the closing**, although the rule says nothing follows "Best regards,". Counted by hand above.

**Model:**

7. The 8 B model is clearly better on content (facts 13 vs 6 of 32, asks 6 vs 1 of 10) and slower (≈ 3 × the latency); the 3 B model leaves more placeholders and more gaps. Both greet the recipient now and then, invent or confuse names and genders ("Mr. Bell" for Grace, "Madame Moreau" for Lucas), sign with a name against the rule, and decide for the owner when other people's requests are in their input.

## What this means for "model or system"

Both, and the system's share is larger than the facts table suggests: the most harmful outcome of the run (finding 1) is ours and model-independent, and nine of the 96 email runs (48 per model) lost their draft to findings 2 and 3, none to the model's writing. The model's share shows in content: the 8 B model roughly doubles facts and asks with the same input.

## Scored again with revision 2 of the checks

Same saved replies, no model call (`--rescore`; outputs `runtime/reply-benchmark/A-3b-checks2/`, `B-8b-checks2/`). Revision 2 accepts the sender's last name in the greeting and flags a name after the closing.

| Replies expected | A rev. 1 | A rev. 2 | B rev. 1 | B rev. 2 |
| --- | --- | --- | --- | --- |
| Greeting right (of drafted) | 13 / 29 | **25 / 29** | 13 / 34 | **25 / 34** |
| Facts mentioned | 6 / 32 | 6 / 32 | 13 / 32 | 13 / 32 |
| Asked for | 1 / 10 | 1 / 10 | 6 / 10 | 6 / 10 |
| Without violation | 12 / 29 | **5 / 29** | 12 / 34 | **1 / 34** |
| Signatures flagged (all 96 runs incl. no-reply-expected) | — | 28 | — | 42 |

Almost every B reply signs a name, so "without violation" drops to 1; the signature guard of step 13 (fix 5) removes such lines from now on.

