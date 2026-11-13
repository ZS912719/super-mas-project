---
id: mailbox-draft-review
type: implementation-summary
scope: team-development
updated: 2026-11-13
status: feature-branch
source_ref: 7e96d933e52db3217a41388dcffbe2b2ec9d1ba2
---

# Desktop Mailbox Drafting and Review

The feature branch `codex/mailbox-draft-review` at `7e96d933e52db3217a41388dcffbe2b2ec9d1ba2` extends the existing agent bridge on `prototype` at `89e148f`. The remote branch was verified at this commit on 2026-11-13. The feature is pending merge into prototype.

In the demo workspace, the Node mailbox calls the loopback Java AgentService when configured. It checks the reply schema, review status, board ID and Java draft version. The mailbox associates the proposal with the session and selected message. Saved drafts and edits retain the proposal and its original warnings. Approval applies to one saved mailbox draft version. Saving edits clears the previous approval. The editor ignores generation results after a message change or an edit. The reply-need check sends only the selected demo message.

Use the template provider and a dedicated demo data directory as shown in the code-branch `mailbox/README.md`. Gmail and Outlook continue to use the direct local model adapter. Approvals stay within the mailbox session. Provider submission, sent-history synchronization and restart recovery are outside this integration.

Source paths on the feature branch: `mailbox/server.js`, `mailbox/lib/agents.js`, `mailbox/lib/drafts.js`, `mailbox/web/app.js`, `mailbox/test/agents.test.js`, and `docs/plans/agent-service-api.md`.

On 2026-11-13, all 15 mailbox tests passed, the frontend built, and 30 selected Java tests passed (AgentServiceTest, BlackboardCollaborationTest and ReplyRepairsTest). A loopback smoke test using real Node and Java processes, synthetic demo mail, and the Java template provider verified generation, saved provenance, editing, rejection of the stale approval, and approval of the current version. No interactive Electron, live mailbox, model inference or sending was exercised in that smoke test.

## Implementation approach on the feature branch

Commit `0cfce67244bc1dff31fe3f62d37783ac1de3f7fe` on `codex/mailbox-draft-review` adapts the [Ponytail decision ladder](https://github.com/DietrichGebert/ponytail) in the code branch's root `AGENTS.md`. It asks contributors to trace the affected flow and prefer existing repository code, standard libraries, platform facilities, and installed dependencies. The guidance preserves validation, error handling, readability, accessibility, and relevant regression coverage. The implementation prompt is in `docs/implementation-prompts.md`. The documentation diff passed `git diff --check`; application tests were not rerun.

## Service readiness before rollout

On 2026-11-13, commit `df0ef5bbb36827fefc62da7ff779069fc03be19d` on `codex/mailbox-draft-review` added authenticated Java readiness, a mailbox settings status check, and explicit service request limits. The Java service admits one generation at a time. Its second HTTP worker can answer health and reply-need requests during generation. Readiness reports the draft slot and does not verify model inference. See `src/main/java/org/supermas/prototype/AgentService.java`, `mailbox/lib/agents.js`, `mailbox/server.js`, `mailbox/web/app.js`, and `docs/plans/agent-service-api.md`.

All 15 mailbox tests and 31 selected Java tests passed, and the mailbox frontend built. A synthetic smoke test with actual Node and Java processes verified unavailable-to-ready recovery and draft generation, save and approval through the template provider. Interactive Electron, live accounts, and local model inference were not exercised. The Java bridge remains scoped to demo mail. The remote feature branch was verified at this revision.
