---
id: team-grafana-observation
type: implementation-summary
scope: team-development
updated: 2026-11-08
status: published-code
---

# Grafana Blackboard Observation

## Source and delivery status

Published to `origin/prototype` on 2026-11-08 at **`f9151ac97f0f0654f5c215030a1627e11e55f115`**, based on `5504b71e925bd25765ef7462e974e4b040aad0c3`. The remote prototype tip was read back at this exact code revision after the push. The feature was split into five focused Conventional Commits:

| Commit | Scope |
| --- | --- |
| `289718f` | Persist explicit participation eligibility |
| `a513ba0` | Add bounded read-only blackboard API and indexed reads |
| `22a324c` | Provision the Grafana workflow dashboard |
| `1b6b03a` | Add the reproducible Docker validation stack |
| `f9151ac` | Document deployment and field semantics |

A subsequent documentation-only commit, `2b8bba3dadc3097a651a9b315bbe7e60a1379cb8`, was pushed and read back as the prototype tip on 2026-11-08. It adds the English reproducibility prompts in `docs/implementation-prompts.md`, moves the README progress table to `docs/project-progress.md`, and maintains the agent workflow. No runtime behavior changed after the validated feature revision above. The prompt history is explicitly reconstructed, not an execution transcript.

This note belongs to a separate knowledge-only update based on `4c093437aa5b8b35175432350e23bac71a8979b3`; application code is not copied into the knowledge tree. Validation below was performed on the working-tree changes now recorded in the code series. Only task-related knowledge is included in this publication.

Changed/new code paths: `src/main/java/org/supermas/observation/`, `src/main/java/org/supermas/control/ControlEntry.java`, `src/main/java/org/supermas/control/AgendaStrategy.java`, `src/main/java/org/supermas/blackboard/persistence/sqlite/SqliteBlackboardStore.java`, `src/test/java/org/supermas/observation/`, `observability/grafana/`, `scripts/start-observation.ps1`, `scripts/start-observation-grafana.ps1`, `scripts/start-observation-docker.ps1`, `scripts/verify-observation-grafana.py`, `docs/blackboard-observation.md` and `README.md`.

## Architecture and access

A separate Java process exposes loopback-only GET `/boards` and `/view` endpoints, authenticated by an environment-injected bearer token and restricted to exact authorized board keys. This is a trusted developer capability, not a specialist view or multi-user permission service. SQLite remains authoritative. `ObservationSource` has no mutation/recovery methods. `SqliteObservationSource` uses `mode=ro`, `query_only=ON` and short bounded read transactions, closing SQLite before decoding/HTTP work. It never constructs the command store, renews/recover leases, acknowledges projections or runs agents/models/mailbox actions.

Application initialization now creates an additive `attempts_by_board_revision` index. The observer requires it and never creates it; older databases need normal updated application initialization before observation. Missing index produces an unavailable response instead of a full-table scan. No board version is changed by this index addition.

Current limits: 100 exact board authorizations, latest 128 immutable snapshots, 1,000 attempts and 1,000 output rows per section; 4 MiB per snapshot, 16 MiB captured payloads, 2 MiB HTTP response. Limits fail closed; retained history explicitly exposes truncation. There is no pagination cursor or cross-query snapshot lease. Snapshot and attempt queries use board/version indexes. The service has two workers and a bounded queue. Large-board throughput is not benchmarked.

Default projection excludes mail/draft/evidence text, prompts, feedback, frozen configurations and raw lease tokens. Reasons/failure messages are redaction fingerprints unless diagnostic output is explicitly enabled for approved data. Diagnostics may contain private free text; it is not suitable for public dashboards. Board scope is authorization; agent filtering is presentation only. Runtime state is never copied to this vault.

## Dashboard and field meaning

Deployment pins are Grafana 13.1.2 and Infinity 4.1.6, with backend JSONata queries and environment-injected secure datasource credentials. The initial 3.11.3 documentation pin was rejected by the actual plugin catalog and is superseded. Native Windows launchers still require an existing distribution/plugin; that distribution has not been executed. A Docker Desktop synthetic validation stack is now supplied by the Dockerfile and compose.yaml under observability/grafana/. Its API remains on container loopback; only Grafana is published on host loopback. Windows-hosted application databases are not mounted.

Panels cover authorized-board count/no-data, observation age, current board overview, expert assessment/current work, State timeline, control/execution/cancellation records and Node graph. Source fields, units, startup and reproduction commands are defined in code-branch `docs/blackboard-observation.md`.

- Each API view captures one database/board revision; separate Grafana queries may race. Every row exposes source revision and observation time; graph details expose their own query revisions.
- Event time differs from collection time. Execution finish is recorded; control/cancellation time is only the first retained snapshot commit containing the record, not an exact decision timestamp. Current control provenance often uses mail receipt time.
- `ControlEntry.Rated.eligible` is an additive nullable value persisting the strategy's existing computed eligibility. New suitability records remain distinct from execution status; old/routing-only records without eligibility stay unknown. The six-argument constructor and older JSON remain readable. No agent-selection algorithm is changed.
- RUNNING_RECORDED does not prove lease validity. Expiry is not inferred from an exception or clock. Recovery/abandonment failures are shown only when recorded. Finished success is a historical result, not a current expert state.
- RUNNING timeline intervals require actual start/finish. PENDING intervals use adjacent immutable commit boundaries. Per-expert segments are split at recorded boundaries into disjoint chronological intervals. Contradictory PENDING/RUNNING overlap is OVERLAPPING_RECORDS; supporting task IDs remain available. This avoids Grafana 13.1.2 end-boundary null insertion truncating a later running interval. Unknown/open/zero-duration bounds remain gaps; cancellation is a recorded point event.
- Contribution originals/promoted copies deduplicate by structured contribution ID. A contribution can be stored even if an execution later fails. Model-call success, composer adoption, semantic request satisfaction and Electron approval/sending remain separate unknowns.
- Graph edges represent recorded authorship, declared coverage, explicit excerpt references and attempt ownership. Attempts use hashed fencing identifiers and drafts use versioned nodes. No chronological dependency or contribution-to-draft adoption is fabricated. Drafts lack structured adoption receipts; some coordinator early exits lack control entries.
- Empty successful arrays differ from 4xx/503 failures. Collection age uses backend time and warns after 45 seconds/stales after 90 seconds. Grafana may retain older values with query errors; errors and timestamps take precedence. Paused browsers cannot continuously recompute freshness. Docker overlays/rendering were verified in the follow-up below; native Windows Grafana remains unverified.

## Validation and limits

On 2026-11-07, `mvn -B -ntp '-Dtest=ObservationTest,ObservationWorkflowTest,ObservationDashboardTest,BlackboardCollaborationTest,LeasedCoordinatorTest,BlackboardTest,BlackboardPrototypeChangesTest' test` passed **57 tests, 0 failures, 0 errors, 0 skipped** (17 new observation checks and 40 existing regressions). The loopback HTTP tests required the authorized execution environment after the restricted sandbox rejected Java loopback connections; no security setting was changed. `mvn -B -ntp -DskipTests package` passed separately. PowerShell parser checks, JSON/static dashboard contract checks and PyYAML provisioning parses passed. Whitespace checks passed. The complete Maven suite and hosted CI were not run.

Tests use synthetic mail, controlled agents and fixed output through the actual prototype LeasedCoordinator/AgendaStrategy. They cover multiple contributors, fallback, failure/retry, rejection/revision, expired-lease polling without authority-table changes, cancellation, recorded recovery/abandonment, board isolation, redaction/auth/query failures, bounded capture, concurrent snapshot consistency, index use and successful empty discovery. Static dashboard contract checks do not constitute Grafana UI validation.

At the initial code-only checkpoint, Docker/Grafana were unavailable. That limit is superseded by the Docker follow-up below; native Windows Grafana startup remains unverified. No real email, paid model, external sending, new JavaFX/live-board integration, full production scheduler, full Maven suite or hosted CI validation is claimed.


## Docker and browser follow-up: 2026-11-08

The validated implementation is now published on prototype at `f9151ac97f0f0654f5c215030a1627e11e55f115`. Docker Desktop 4.98.0 with its Linux engine, Compose 5.6.1, Grafana 13.1.2 and Infinity 4.1.6 were actually run. Base images are digest-pinned; the Java image resolves to Temurin 17.0.21+9. The signed plugin was installed from the official catalog without an unsigned-plugin exception. No host security/network setting or system service was changed.

The stack has a separate fixture job, read-only API container and Grafana container. Grafana shares the API network namespace, so its data source can still use container localhost. Only host 127.0.0.1:3000 is published; API port 8091 is not published. The API mounts the sealed synthetic SQLite fixture and its root filesystem read-only, with a bounded executable tmpfs for bundled JDBC JNI loading. Grafana has no mount of the application fixture. Fixture initialization runs controlled real coordinator workflows and then seals its own database; repeated startup checks its marker and preserves the database byte-for-byte. This does not validate cross-OS live SQLite access or connect the user's Windows application database.

ObservationFixtureServer is test-only: a read-only controls mount supplies stale timestamp, empty discovery and controlled error cases without changing SQLite. Its diagnostic strings are enabled only for synthetic data. The shipped start script recreates namespace-sharing containers together and preserves their volumes; it accepts environment credentials or a protected ignored environment file. There is no floating image/plugin version in the deployment configuration.

Actual checks:

- The initial follow-up combined Maven run passed 58 tests (18 observation plus 40 existing regressions). After native-interval fixes, the final focused command `mvn -q '-Dtest=ObservationTest,ObservationWorkflowTest,ObservationDashboardTest' test` passed 20 tests, zero failures/errors/skips. These include sealed-fixture restart, chronological intervals, and a ticking-clock reproduction of lease start preceding commit. This is not a full-suite or hosted-CI claim.
- The delivered start script succeeded and preserved the fixture bytes through container recreation. `python scripts/verify-observation-grafana.py --fault-tests` passed against all three synthetic boards, including every actual Infinity panel query, values/row counts/revisions versus the read-only API, expert filtering, stale age, empty discovery, 503 failure without successful stale frames and recovery. No credentials are printed by that verifier.
- Actual browser checks passed for board/expert switching, current overview values, normal empty event windows, one timeline row per expert, waiting/running/gap rendering, native Node graph relationships, red 120-second stale data, gray UNAVAILABLE/error indicators for both a controlled 503 and a genuinely paused API container, and recovery. The API was unpaused and controls cleared afterward. A raw version-number graph fallback and green missing-data stat defaults were corrected during this check.
- Final PowerShell/Python/JSON/YAML, image-pin and container-boundary checks passed; the final `mvn -B -ntp -DskipTests package` also passed on 2026-11-08. Screenshots and synthetic HTTP artifacts stay in ignored runtime storage, outside shared knowledge.

Limits: Grafana lazily queries visible panels; retained offscreen rows are not a fresh capture. Absolute historical ranges can turn auto-refresh off, and board-discovery options may require a dashboard reload after a discovery outage/empty result. The current sealed fixture keeps its original event times; select the recorded range to view history. Native Windows Grafana, real mailbox/model calls, large-board load, hosted CI, model-call receipts and structured contribution-to-draft adoption remain unverified or absent as described above. The prototype coordinator remains a stand-in. The code publication was remotely verified; this knowledge update follows the separate knowledge-branch workflow.

## Follow-up and rollback

Code and knowledge are delivered as separate commits on their respective branches. Verify the current branch, source revision and latest knowledge before extending this feature; this dated publication does not certify future code or hosted CI.

Rollback of the published implementation should use ordinary reverting commits in dependency order, preserving other work and published branch history. Record the rollback revision and actual checks in the affected notes. Stopping the observation/Grafana processes does not alter blackboard state. The additive application index may remain safely when rolling back the observer; older eligible-less control entries continue to work. No rollback was performed in this task.

Related: [Blackboard-Storage-and-Observation](Blackboard-Storage-and-Observation.md), [Generation-UI-Integration](Generation-UI-Integration.md), [Build-CI-and-Delivery](Build-CI-and-Delivery.md), [Validation-and-Limits](Validation-and-Limits.md).
