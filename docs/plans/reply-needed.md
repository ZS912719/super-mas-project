# Plan: is a reply needed?

Approved by the project owner on 2026-11-11, after the human rubric on real mail (benchmark protocol B68): in 10 of
20 sampled Enron emails no reply was needed, yet the system drafted one for every email. The synthetic reply checks
already record `replyExpected` (14 of 24 `synthetic-dev` emails need no reply); the system did not use it.

## Decisions of the owner

- **Action:** advise everywhere; in batch runs (`.eml` prototype, benchmarks) stop the board without a draft when no
  reply is needed (existing status `STOPPED`, reason recorded). In the mailbox, **Generate** always drafts when clicked.
- **Mailbox:** a short note next to Generate ("Probably no reply needed: …") and an inbox label.
- **Owner setting:** the owner's address(es) are configured, so the owner's own sent mail needs no reply.
- **Test set:** the owner labels the 100 frozen Enron test emails (reply needed: yes / no / unsure) on a blind
  labelling tab, before the classifier is run on them.

## Design

1. `metadata.ReplyNeedClassifier`: deterministic rules, verdict `NEEDED`, `NOT_NEEDED` or `UNSURE`, always with
   reasons. Built only on `synthetic-dev`, `synthetic-multi` and the Enron dev set, never on the test set. Signals:
   sender is the owner; automated or bulk mail (no-reply senders, list headers, "automated e-mail", "do not reply");
   broadcasts (group addresses, many recipients); thanks or acknowledgement only; information only (no request or
   proposal in the speech acts and the request extraction). `UNSURE` is treated as `NEEDED`: a missed reply costs
   more than an extra draft.
2. The verdict travels in the email's metadata (`replyNeed`, `replyNeedReasons`). The prototype wiring stops a board
   whose verdict is `NOT_NEEDED` in batch mode; the agent service never stops (the user asked for a draft) and returns
   the verdict as `replyNeed` in its response.
3. Mailbox: the note and a label from the agent response (UI change agreed by the owner).

## Measurement (rules fixed and committed before the test run)

- Confusion matrix and precision of `NOT_NEEDED` on `synthetic-dev` (development) and on the owner's 100 Enron labels
  (test, with bootstrap intervals). Key number: emails judged `NOT_NEEDED` that the owner labelled as needing a reply.
- No harm: every reply-expected synthetic email must still get a draft in the main configuration.
- Effect on real mail: a W9 re-run counting unneeded drafts avoided.
- Every run in `docs/benchmark/PROTOCOL.md`; results in the LaTeX chapter.

## Order

1. Blind labelling tab (`replyneed`) for the owner.
2. Classifier with unit tests, owner setting, metadata, batch stop, agent API field.
3. Development measurement; freeze; then the test measurement on the owner's labels.
4. Mailbox note and label.
