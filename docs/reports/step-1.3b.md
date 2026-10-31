# Step 1.3b: Stripper Rules for Real Mail

This step is not in the plan. I added it because step 1.3's stripper scored about 0.58 on real mail, after scoring 1.000 on the synthetic set I wrote myself.

## What was done

- Reviewed every dev disagreement, using `StripperDiff`, a new tool that prints where the stripper and the labels differ.
- Rewrote `QuoteSignatureStripper` with these new rules:
  - Lotus Notes forward banners, and lines ending in a timestamp.
  - A date line directly below a name, which removes both lines.
  - Attachment markers, ruler lines (`*****`, `=====`) and machine footers.
  - Multi-line signature blocks containing contact details.
  - Keeping a postscript after a sign-off.
  - Always keeping the last block, so a message like "FYI" or a bare link survives.

## Results

| Set | Before | After |
| --- | --- | --- |
| Enron dev, exact `currentText` matches | 57 of 100 | 80 of 100 |
| Synthetic set, `currentText` F1 | 1.000 | 1.000 |

The dev labels are still my unreviewed drafts. **These numbers are not an unbiased estimate.** I tuned the rules on these same 100 emails against labels I wrote. The frozen test set will give the honest figure, and I expect it to be lower.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Keep a rule-based stripper | The plan says facts come without a model. Rules are fast and explainable | Training a zone classifier such as Quagga: more accurate in the literature, but a large addition, and its labelled data is deferred |
| Stop at 80 of 100 | Most remaining errors are one-off cases. Adding a rule for each would fit this sample and not generalise | Rules for every remaining case |
| Line-level decisions | The stripper's existing design, and easy to reason about | Character-level or sequence models |
| Keep the switches | The plan requires each stage to be measurable alone | Removing the flags |
| Add a `StripperDiff` tool to test scope | It never ships, and only reads dev labels | Reading benchmark totals, which do not show causes |

## Problems encountered

- **Sign-offs in the middle of text.** A sign-off on its own line now only starts a signature. Text after it is dropped unless it is a postscript, which can lose real text.
- **The first version cut too much in some emails,** because new rules fire on legitimate content. These cases remain:
  - t0183: `Request Create Date : 1/7/02 5:01:54 PM` ends in a timestamp and cuts the rest.
  - t0193: a block of dial-in numbers is treated as a signature.
  - t0181: a ruler line of stars inside a marketing email ends the text.
  - t0061: a `Sincerely` inside a sentence of a promotional footer ends the text.

## Remaining errors on dev (20)

| Kind | Examples |
| --- | --- |
| In-line sign-offs, which line rules cannot see | `...almost there. BT`, `Thanks. Mark` |
| Over-cutting by the new rules | t0061, t0181, t0183, t0193 |
| Sign-offs not listed | `All the best, and Happy New Year!`, `Take it easy and w/b,` |
| Machine text not recognised | t0074 web form diagnostics, t0107 `Detailed description:` |
| Disagreements over label conventions | t0038 trailing dots; t0123 name inside a table |

## Critical points

- **The rules are tuned to Enron's 2000-2002 mail.** Lotus Notes banners may be rare in modern university mail. They do no harm there, but the accuracy gained here will not carry over. A real university mail set is the only way to find out, and that data is blocked.
- **French rules got no new work,** in line with the English-only decision. They are checked only on the synthetic set.
- **Over-cutting matters more than under-cutting.** Losing a request at the end of an email removes what the reply must address, and later stages cannot recover it. Under-cutting only adds noise. The scorer treats both as the same kind of failure.
- **`unsubscribe` as a boundary is crude.** A real sentence mentioning the word ends the text.
- **Exact match is harsh.** One extra word counts as total failure, so 80% understates partial usefulness. A token-overlap score would add useful information, but the benchmark does not have one yet.
- **Rule order matters and is only lightly tested.** Unit tests cover the older rules. The new rules are checked mainly by the dev comparison, which is ignored by Git and absent on other machines.
