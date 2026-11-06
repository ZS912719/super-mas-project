# Prototype Session, 2026-11-06

What was done to build the first working prototype, why, and what needs a decision. Everything is on the new local branch `prototype`. **Nothing was pushed.**

**State at the end:** `mvn test` passes with **307 tests, 0 failures, 0 skipped** (256 at the start: 278 after the merge, then 29 new tests), and `npm test` in `mailbox/` with **14, 0 failures** (the teammate's 11 unchanged, 3 new). `PrototypeApp` takes the 24 synthetic emails to 24 drafts awaiting review on the SQLite blackboard, and the desktop mailbox can get its drafts from the agents (step 11.6).

## 1. Summary

1. **Branch `prototype`** = `origin/main` + our `docs/metadata-context-plan` + the teammate's `codex/blackboard-storage` (durable SQLite blackboard, Markdown for Obsidian, formal models, desktop mailbox). The knowledge branch was read, not merged, as its rules require.
2. **The teammate's blackboard is used unchanged.** Its rules are stricter than our stand-in, so our side adapted: every agent action is now a leased task; agents submit private evidence; a coordinator stand-in promotes context findings to the agents allowed to read them.
3. **Nothing measured changed.** The C3 benchmark on Enron gives identical quality, count and isolation figures; the role benchmark output is identical byte for byte.
4. **A runnable prototype exists:** `.eml` → metadata → memory → topics → context → missing-information check → routing → specialist → plan → template draft, awaiting review on SQLite, visible in Obsidian, state kept between runs.
5. **The mailbox asks the agents** when `AGENT_SERVICE_URL` is set: drafts from the routed specialist, the role as label on a clear match, review and sending unchanged (step 11.6).
6. **Findings for the teammate:** their board drops a task's information need when the task is claimed (one-line fix), and it does not check that only reply agents may draft.

## 2. How to review and undo

| Goal | Command |
| --- | --- |
| This session's commits | `git log --oneline --first-parent before-prototype-merge-2026-11-06..prototype` |
| Only our work after the merges | `git diff 6ba7756..prototype` |
| Run all tests | `mvn test` |
| Run the prototype | `mvn -q compile exec:java -Dexec.mainClass=org.supermas.prototype.PrototypeApp -Dexec.args="benchmark/data/synthetic-dev"` |
| Throw the branch away | `git switch docs/metadata-context-plan && git branch -D prototype` (both source branches are untouched) |

| Commit | Step | What |
| --- | --- | --- |
| `29af6f9` | 11.0 | Merge `docs/metadata-context-plan` into `prototype` |
| `6ba7756` | 11.0 | Merge `origin/codex/blackboard-storage`; `pom.xml`, `.gitignore`, `README.md` conflicts |
| `548e90a` | 11.1 | Imports moved to `blackboard.api` and `blackboard.persistence` |
| `2f4d85a` | 11.2, 11.3 | `LeasedCoordinator`, `EvidencePromotion`, private contributions |
| `fbc414e` | 11.4 | `PrototypeApp` and its test |
| `9727574` | 11.5 | Reports, stand-in list, plan, README, architecture, CLAUDE.md |
| `eae2c00` | 11.6 | `AgentService` (Java, loopback `/v1/reply`) |
| `3fb59ef` | 11.6 | Mailbox: `lib/agents.js`, opt-in branch in `/api/generate`, tests |
| (docs commit) | 11.6 | Contract proposal, mailbox README section, step report |

Reports: [11.0-11.1](step-11.0-11.1.md), [11.2-11.3](step-11.2-11.3.md), [11.4](step-11.4.md), [11.6](step-11.6.md).

## 3. Decisions taken and by whom

| Decision | By | When |
| --- | --- | --- |
| Merge into a new branch `prototype` | Project owner | Request, 2026-11-06 |
| Evidence promotion: "we use the code of the other branch", so their board's rules stand and our side adapts | Project owner | 2026-11-06 |
| No model for drafts now; a local model is the perspective | Project owner | 2026-11-06 |
| Mailbox connection as the last step | Project owner | 2026-11-06 |
| A separate branch because the teammate asks for manual verification and review | Project owner | 2026-11-06 |
| Mailbox: routing becomes labels; the mailbox UI is not changed | Project owner | 2026-11-06 |
| Labels only on a clear routing match, so fallbacks never overwrite labels | This session, after the live run | Step 11.6 report |
| How promotion decides readers (config + document readers), `@shared` copies, one lease per assessment, history failures as `recordFailure` | This session, within the approved plan | Justified in the 11.2-11.3 report; a proposal for the team (open decision 10 in `docs/architecture.md`) |

## 4. What needs you or the team

| # | Item | Who | Suggested action |
| --- | --- | --- | --- |
| 1 | `BoardState.transition` drops `Task.need` | Teammate | One-line fix: pass `task.need()`. Worked around meanwhile |
| 2 | The durable board accepts drafts from any leased agent (P5 not enforced) | Teammate + team | Decide whether the board or the coordinator enforces it |
| 3 | The promotion rule (open decision 10) | Team | Validate or replace the prototype's rule |
| 4 | The mailbox is merged unreviewed | Teammate | Their review before anything reaches `main` |
| 5 | Whose mailbox: the roles assume staff answering students, the mailbox demo is a student's or researcher's inbox, so demo mail always falls back | Team | Decide the target user before tuning roles |
| 6 | The mailbox changes (10 lines in `server.js`, one new module, one new test file) | Teammate | Review: their module |
| 7 | The agent service contract (`docs/plans/agent-service-api.md`) | Team | Validate or replace; it is a proposal |
| 8 | The team knowledge vault does not know about `prototype` yet | Project owner / teammate | A separate knowledge commit on `team-knowledge-vault`, as its workflow requires. Not done here |
| 9 | CI has not run on `prototype` | Project owner | Runs on first push |

## 5. Next steps

1. Run the Electron window once with the agents (`npm start` in `mailbox/`, after a full `npm ci`), untested here.
2. A local-model `GenerationService` behind the existing interface, replacing the template.
3. The remaining open items of Phase 7 (UML and docs for validated contract changes).
