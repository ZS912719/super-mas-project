# Blackboard Runtime Validation

Validated on 2026-11-04 on Windows, on branch `codex/blackboard-storage`.

The implementation has been reorganized under `blackboard.api`, `blackboard.impl`, `blackboard.model`, `blackboard.persistence` (with `codec`/`sqlite` adapters), `blackboard.export` and `blackboard.app`. `DefaultBlackboard`, its factory and the exporter depend on the new `BlackboardStore` contract. There is no `org.supermas.runtime` source package. The 22 tests are split into 13 blackboard/task integration tests and 9 Markdown projection tests, with shared test fixtures in `blackboard.testing`.

After the package refactor, a clean rebuild passes the same tests, and strict audit successfully reads the existing revision-5 SQLite database and its generated documents without a data migration. The Java package names changed; imports and CLI class names have been updated. Persistent identities and JSON formats are preserved. The initial Obsidian UI check below is from the first implementation; the package-only refactor was checked through file/database audit rather than repeating UI navigation.

## Automated checks

`mvn package` passes with 22 tests, zero failures/errors/skips. The suite uses temporary databases and covers:

- Competing connections and independent JVM processes: exactly one task claim succeeds.
- Lease expiration, safe requeue, uncertain-work failure, restarted store and fenced late completion.
- Exact completion retry, conflicting retry rejection, stale input rejection and explicit abandonment.
- Heartbeat without business-revision change and frozen prompt persistence after completion.
- Legacy execution/approval bypass rejection, atomic rollback and outbox consistency.
- Source/author/audience validation, detached state, and project/thread isolation.
- Historical faithful documents with visible revision lag and eventual reconciliation.
- Interrupted generation and publication before acknowledgement.
- A real subprocess `Runtime.halt` immediately after publication, followed by successful recovery.
- Corrupted Markdown with a recomputed manifest hash: semantic audit still rejects it.
- Deleted entry regeneration, publisher lock exclusion and database/vault identity mismatch.
- Automatic export retry after a transient failure and bounded SQLite writer contention.
- Knowledge edits do not mutate authoritative state; raw execution tokens are not exported.

These checks demonstrate tested process-crash windows. Arbitrary power loss, filesystem metadata durability, malicious local processes and exactly-once external tool execution are not established.

## Kripke model checking

The official Windows nuXmv 2.1.0 binary was downloaded into ignored temporary storage. Its SHA-256 matched the official checksum. `scripts/check-formal.ps1` passes:

| Run | Result |
| --- | --- |
| Task lifecycle | 3 safety invariants and 2 reachability properties true |
| Projection protocol | 4 safety invariants, 1 reachability property and 1 LTL property true |
| Safety without fairness | All 7 invariants true |
| Broken fencing negative control | Expected stale-completion counterexample |
| Broken projection negative control | Expected document/history mismatch counterexample |

Models are bounded abstractions. Progress requires eventual environment stability and fair exporter scheduling; it has no wall-clock deadline. The combined Java implementation is tested, not formally refined from the models. Reproduction commands and assumptions are in [formal documentation](../formal/README.md). Full generated logs are in `target/formal` and are not versioned.

## Demonstration and viewer check

`scripts/run-blackboard.ps1 -Mode demo` completed without model/provider calls. Its board reached revision 5: pending task, claim, proposed draft and successful completion. The board itself remained `PROCESSING`; task completion does not automatically decide overall board status. Both export and strict audit reported `valid=true`, `documentRevision=5`, `databaseRevision=5`.

The generated vault was opened in the installed Obsidian 1.15.0 application using the computer-use skill. The `current.md` entry displayed source revision 5 and opened its matching `overview.md`. The displayed metadata contained `source_revision=5`; task progress showed `SUCCEEDED` with action `GENERATE`. The snapshot body displayed the committed task, proposed draft and execution-attempt context. The existing unrelated vault was preserved.

This verifies initial file discovery, link navigation and rendering. It does not establish that an already-open Obsidian tab refreshes within a time bound after every subsequent database update. Continuous cache freshness and automatic jumping from an old immutable generation to a newer generation remain separate UI concerns.

Five PlantUML diagrams pass syntax validation and were regenerated as SVG/PNG, including the runtime and updated package inventory. `git diff --check` passes. Runtime state and local project-progress notes are ignored by Git.

## Remaining boundaries

- No real specialist runner, automatic routing, model invocation or mailbox integration.
- No automatic knowledge-file import or retrieval; trusted configurations are supplied through the Java API and frozen at claim time.
- No human-review implementation, provider sending or automatic retries for uncertain external effects.
- Any unrelated revision on a board invalidates existing attempt inputs; dependency-specific validation is deferred.
- No general schema migration, snapshot retention, old-generation cleanup or multi-host database deployment.
- SQL command failures do not imply safe replay of non-idempotent contributions; only terminal completion has a durable retry receipt.
