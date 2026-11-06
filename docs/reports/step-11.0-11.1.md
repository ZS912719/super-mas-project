# Steps 11.0 and 11.1: The `prototype` Branch and the Merge

## Outcome

A new branch `prototype` joins this member's work (`docs/metadata-context-plan`) with the teammate's work (`origin/codex/blackboard-storage`, which contains `feat/mailbox-desktop`) on top of `origin/main`. It compiles, and `mvn test` passes with **278 tests, 0 failures, 0 skipped** (256 ours, 22 theirs). Nothing was pushed. The branch has no upstream, so a plain `git push` cannot send it by accident.

| Commit | What |
| --- | --- |
| `29af6f9` | Merge `docs/metadata-context-plan` into `prototype` (created from `origin/main` at `d62e4fc`). No conflicts |
| `6ba7756` | Merge `origin/codex/blackboard-storage`. Conflicts in `pom.xml`, `.gitignore`, `README.md`, resolved by keeping both sides |
| `548e90a` | Step 11.1: our imports moved to the teammate's new packages |

Tag `before-prototype-merge-2026-11-06` marks `origin/main` before the merges.

## What was on the other branches (inspected 2026-11-06, after `git fetch --all --prune`)

| Branch | Author | Content | Used |
| --- | --- | --- | --- |
| `origin/main` at `d62e4fc` | teammate | Two commits after our base: README cleanup ("STOP AI SLOP"), then GitHub Actions CI and release workflows, packaging scripts, `docs/ci-cd.md` | Yes: the base of `prototype` |
| `origin/codex/blackboard-storage` at `6ff871f` | teammate | Durable SQLite blackboard with task leases and fencing, Markdown projection for Obsidian, nuXmv formal models, 22 tests, contracts reorganised into `blackboard.api`, `.impl`, `.model`, `.persistence`, `.export`, `.app`. Also contains the desktop mailbox | Yes: merged |
| `origin/feat/mailbox-desktop` at `c039b37` | teammate | Electron/Node mailbox, "[NEED REVIEW] … review before merge" | Already inside `codex/blackboard-storage`; not merged separately |
| `origin/team-knowledge-vault` at `2bb48f9` | teammate | Shared Obsidian notes only; its tree deletes all code | **Not merged.** Its own `AGENTS.md` forbids merging it into code branches. Read with `git show`, as it asks |

## Choices and why

| Choice | Why | Alternative rejected |
| --- | --- | --- |
| Base on `origin/main`, then merge ours, then theirs | `prototype` gets the CI commit that `codex/blackboard-storage` lacks, and both feature branches enter the same way | Basing on one feature branch: the other side would look like the "foreign" one in history |
| Real merges (`--no-ff`), not a rebase | Keeps both authors' commits and dates exactly as they were; nothing is rewritten | Rebase or squash: would rewrite the teammate's commits |
| `pom.xml`: all dependencies of both sides; Jackson 2.18.2 → **2.21.4**, JUnit 5.11.4 → **5.12.2** (theirs, newer); added `sqlite-jdbc` 3.54.1.0, `jackson-datatype-jdk8`, `jackson-datatype-jsr310` | One version per library on one classpath. Their JSON codec needs the Jackson modules; ours works on the newer Jackson unchanged (full suite passes) | Keeping our older versions: their code was tested on the newer ones |
| `.gitignore`: union; one duplicate `/runtime/` line dropped | Both sides' private data stays ignored | — |
| `README.md`: their structure, plus `main`'s CI paragraph, under their "Desktop mailbox" heading | Their README rules (`AGENTS.md`) govern its content; our packages are added in step 11.5 | — |
| Import migration as its own commit (11.1), not inside the merge | A reviewer sees exactly what changed in our code, separately from the conflict resolution | Folding it into the merge commit: hides edits inside a merge |

## Step 11.1: the package move

The teammate moved `AgentBlackboard`, `Blackboard`, `BlackboardFactory`, `BlackboardView` and `Preprocessor` from `org.supermas.blackboard` to `org.supermas.blackboard.api`, and `BlackboardRepository` from `org.supermas.persistence` to `org.supermas.blackboard.persistence`. **No signature changed.** 24 of our files (15 main, 9 test) needed only their imports rewritten, done mechanically with `sed` and checked by the compiler.

Our only change to a shared record, `Task` gaining an optional `InformationNeed` (proposal P2), compiles with their code, because the six-argument constructor still exists.

## How the merge was checked before it was made

`git merge-tree --write-tree` (no working-tree change) listed the conflicts in advance. The merged tree was exported to the scratchpad, the pom and imports fixed there, and `mvn test` gave 278 tests, 0 failures, 4 skipped. The 4 skipped were GLiNER/ONNX tests, because model files are Git-ignored and so absent from an export. In the real checkout all 278 run.

## Problems encountered

1. **The knowledge branch cannot be merged.** Its tree has no code, so a merge would propose deleting everything. Its instructions say so; it was read, not merged.
2. **`git switch -c prototype origin/main` set `origin/main` as upstream.** Removed with `git branch --unset-upstream`, so that a later push needs an explicit target.

## Critical points

- **The mailbox is merged but unreviewed.** It came in through `codex/blackboard-storage`, still tagged "[NEED REVIEW]". `prototype` is a separate branch for exactly this reason (project owner, 2026-11-06). Nothing here should go to `main` before the teammate reviews it.
- **"STOP AI SLOP!!! Every line written shall be verified manually!!!"** is now the first line of the README. Much of this member's code was written with an AI assistant. Every step is documented and tested, but the teammate may want to review it line by line before `main`.
- **The teammate's `AGENTS.md` now applies on this branch:** Conventional Commits (used from here on), README limited to dependencies, structure, deployment, usage and progress, and loading the team knowledge vault at task start.
- **CI:** the merged GitHub Actions run `mvn -B -ntp clean verify` on Linux and Windows, the mailbox tests on Windows, and nuXmv. They have not run on `prototype`, because nothing was pushed. Our tests need the Git-ignored ONNX models in places; those tests skip themselves when the models are absent, so CI should pass, but this is untested.
