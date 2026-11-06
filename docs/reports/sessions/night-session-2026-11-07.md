# Night Session, 2026-11-07

What was done overnight on the local `prototype` branch, within the scope approved before the session. **Nothing was pushed, no remote was touched, no history was rewritten, no branch was renamed or deleted.**

**State at the end:** `mvn clean package` passes with **328 tests, 0 failures, 0 skipped** (307 at the start of the night), `npm test` in `mailbox/` with **14, 0 failures**. The live stack (Java agent service + mailbox server) was driven end to end on the demo workspace.

## 1. Summary

1. **The blackboard is real** (Phase 12). Emails are split into requests; every specialist that fits contributes for its requests; a composer writes one reply; a controller after Hayes-Roth's BB1 chooses each step from the board and writes the decision on the board; rejection with feedback produces a revised version.
2. **Measured, honestly.** On 24 single-intent emails the blackboard equals the router (15 of 24). On 12 frozen multi-request emails it matches the gold specialists on 5 against 4 (default threshold 0.4), recall 0.57 against 0.48, splits 11 of 12 correctly; at threshold 0.2 it reaches 10 of 12, which is reported but **not adopted**, because it was found on the test data. Cost: about twice the rounds and database writes (≈150 ms per email).
3. **A local-LLM adapter** for the OpenAI-compatible API (Ollama, llama.cpp, LM Studio) is built and tested against a fake engine; the template stays the default because no engine is installed.
4. **Compliance, summaries, to-do and code analysis** are written; three defects found by the review were fixed with regression tests; one high-severity risk (cross-role disclosure through the composer) is documented with a prompt mitigation and a proper fix in the to-do.
5. **The repository is tidier:** references out of `src/`, papers ignored for copyright, session protocols grouped, reports indexed, generated junk deleted.

## 2. How to review and undo

| Goal | Command |
| --- | --- |
| The night's commits | `git log --oneline --first-parent before-night-2026-11-07..prototype` |
| Full diff of the night | `git diff before-night-2026-11-07..prototype` |
| Only tonight's changes to the teammate's code | `git diff before-night-2026-11-07..prototype -- src/main/java/org/supermas/blackboard src/test/java/org/supermas/blackboard` (the mailbox was changed in step 11.6, not tonight) |
| Undo the whole night | `git reset --hard before-night-2026-11-07` (the deleted untracked files cannot be restored this way: `runtime/`, `target/` and two benchmark index caches, all regenerable) |
| Run everything | `mvn clean package` and `cd mailbox && npm test` |
| See the blackboard work | `mvn -q compile exec:java -Dexec.mainClass=org.supermas.prototype.PrototypeApp -Dexec.args="benchmark/data/synthetic-multi"` (prints each email's control trail) |

| Commit | Task | What |
| --- | --- | --- |
| `894f12b` | 1 (12.0) | Design note of the blackboard control |
| `cae151d` | 1 (12.1) | Teammate's board: read-only views, feedback, drafting rule, kept needs; `Action.CONTRIBUTE` |
| `0656dab` | 1 (12.2-12.5) | Request splitter, contributions, composer, agenda controller |
| `d4ad4da` | 1 (12.6) | Rejection with feedback and revision |
| `fb8c553`, `91ee98c` | 1 (12.7) | Frozen multi-request set; router vs blackboard benchmark |
| `ee6c0f0` | 1 (approved extra) | Local-LLM adapter |
| `12d51c3` | 1 (12.8) | Step 12 report and doc updates |
| `259567b` | 5 | Three review fixes with regression tests |
| `84eacfe` | 2, 3, 4, 5 | Compliance check, summaries by part, to-do, code analysis |
| `4da863f` | 6 | Repository cleanup |
| (this commit) | — | This protocol |

## 3. Where to read

| Task you gave | Result |
| --- | --- |
| 1. Implement Phase 12 | [`docs/reports/step-12.md`](../step-12.md), design [`docs/plans/blackboard-control.md`](../../plans/blackboard-control.md), results [`docs/benchmark/results/blackboard-vs-router.md`](../../benchmark/results/blackboard-vs-router.md) |
| 2. Compliance with the statement and the targeted architecture | [`docs/compliance.md`](../../compliance.md) |
| 3. Summaries by project part | [`docs/summary/`](../../summary/README.md) |
| 4. Everything needed for a fully compliant, tested and benchmarked project | [`docs/todo.md`](../../todo.md) |
| 5. Code analysis (bugs, SOLID, patterns) | [`docs/code-analysis.md`](../../code-analysis.md) |
| 6. Git cleanup | Commit `4da863f`; [`docs/references/README.md`](../../references/README.md), [`docs/reports/README.md`](../README.md) |

## 4. Decisions and their owners

| Decision | By |
| --- | --- |
| Scope of the night (Phase 12 in full with recommendations 1-3 and BB1, compliance, summaries, to-do, analysis with safe fixes, cleanup within limits, LLM adapter) | Project owner, before the session |
| Strict successive levels and relevant-event triggering in the controller | This session, after the first versions wasted rounds or lost context (step 12 report) |
| Specialists never read each other's contributions | This session, for confidentiality |
| Default threshold kept at 0.4 despite the sweep | This session, to avoid tuning on test data; **yours to change** |
| Disclosure mitigation in `prompts/composer.txt` | This session, as a low-risk prompt rule; the real fix is in the to-do |
| Untracked models, venv and the Enron archive kept | This session (they are downloads, not generated files) |

## 5. What needs you

| # | Item | Why | Suggested action |
| --- | --- | --- | --- |
| 1 | **Contribution threshold** | 0.2 doubled multi-request accuracy on this set, but it was found on the test data | Approve a fresh validation set (to-do 3.6), then decide |
| 2 | **Install a local engine** (e.g. Ollama) and pick a model | Every remaining experiment (relevance, prompt influence, LLM limits) needs it | `! curl -fsSL https://ollama.com/install.sh \| sh` or your package manager; then I can run to-do 2.1-2.3 |
| 3 | **Review the 12 multi-request gold labels** and the 25 Enron test labels | All accuracy figures are provisional until a person checks them | To-do 3.1 |
| 4 | **Cross-role disclosure** | A staff-only excerpt can reach the composer's prompt for a student email | Approve to-do 6.1 (audience per source) |
| 5 | **The teammate's review** | Their blackboard code and their mailbox server were changed (listed in the step 11 and 12 reports) | Before any merge toward `main` |
| 6 | **Interface items** (show contributors and trail, reject with feedback, agent management) | Statement §10; you asked to leave the mailbox UI untouched | Decide whether and who changes the UI (to-do 4.x) |
| 7 | **Report, UML and contributions table** | Deliverables §16-17 | Team (to-do 1.1-1.3) |

## 6. Incidents during the night

1. **An earlier commit (`32fb1a2`, yesterday) contained the requirements PDF** by accident (`git add -A`). You decided it stays tracked; it is now moved to `docs/references/`. From the night on, only named paths were staged.
2. **A stale agent service from yesterday** (started by a failed `pkill` that killed its own shell) was still listening on port 4318; the first live drive tonight was correctly refused by its token check. It was stopped by port, and the drive script now stops everything by port.
3. **One edit script aborted silently** on a text mismatch before writing; a rerun showed the unchanged behaviour and the edit was redone (step 12 report).

## 7. Next steps (from the to-do, P1 first)

1. A local model run on both synthetic sets (to-do 2.1-2.2), then the relevance and prompt experiments (3.2, 3.3, 3.7).
2. Label reviews (3.1) and the fresh validation set (3.6).
3. Disclosure by audience (6.1) and the SuperMASProject role (5.1).
4. Report, UML, contributions (1.1-1.3) with the team; teammate review (8.1) and team validation of shared changes (8.2).
