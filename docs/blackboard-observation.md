# Blackboard workflow observation

## Deployment and dependencies

The application SQLite database remains authoritative. A separate Java 17 process opens an **existing** database with SQLite `mode=ro` and `query_only=ON`. It has no command-store instance, recovery worker, agent runner, projector, model client or mailbox client. Only the developer operator who already has database access should run it. This is a trusted developer observation capability, not a specialist-agent view or a multi-user authorization service. Explicit board keys restrict its scope; agent filters narrow presentation only, not authorization.

Use the existing Maven dependencies; no Java framework or additional database is introduced. The reproducible Grafana configuration pins **Grafana 13.1.2** and **Infinity 4.1.6** in `observability/grafana/versions.json`. The launcher rejects other versions. This pair was deployed and queried on Docker Desktop on 2026-11-08; the separate native Windows Grafana distribution has not been run. Check security advisories before deploying beyond a local development machine.

Infinity is maintained by Grafana Labs, licensed Apache-2.0, supports backend JSONata parsing and Node graph formats, and its 4.1.6 manifest requires patched Grafana ranges: >=11.6.11 <12, >=12.0.10 <12.1, >=12.1.7 <12.2, or >=12.2.5. Grafana 13.1.2 satisfies this requirement. Grafana must be able to reach the API and execute the plugin's signed Windows backend binary. Backend parsing keeps the observation credential in the data source's encrypted secure configuration rather than dashboard JSON. No unsigned-plugin exception is configured. A direct SQLite data source is deliberately not used: it would couple Grafana to internal persistence and its permission boundary. Prometheus/Loki would add services without providing the relationship data this first version needs.

Official sources reviewed on 2026-11-07 and rechecked for deployment on 2026-11-08:

- [Infinity overview and backend requirements](https://grafana.com/docs/plugins/yesoreyeram-infinity-datasource/latest/)
- [Infinity release and maintenance information](https://grafana.com/grafana/plugins/yesoreyeram-infinity-datasource/)
- [Infinity license](https://github.com/grafana/grafana-infinity-datasource/blob/main/LICENSE)
- [Infinity query and variable types](https://github.com/grafana/grafana-infinity-datasource/tree/main/src/types)
- [Node graph formats](https://grafana.com/docs/plugins/yesoreyeram-infinity-datasource/latest/advanced-features/node-graph/)
- [Grafana Windows standalone deployment](https://grafana.com/docs/grafana/latest/setup-grafana/installation/windows/)
- [Pinned Grafana release](https://grafana.com/grafana/download/13.1.2)
- [Actual Infinity 4.1.6 distribution metadata](https://grafana.com/api/plugins/yesoreyeram-infinity-datasource/versions/4.1.6)
- [Versioned Infinity manifest](https://github.com/grafana/grafana-infinity-datasource/blob/v4.1.6/src/plugin.json)
- [Grafana 13.1.2 timeline interval handling](https://github.com/grafana/grafana/blob/v13.1.2/public/app/core/components/TimelineChart/utils.ts)

### Windows startup

Run from the repository root. Supply an existing development database; never point this feature at an unapproved mail database. Obtain each board key from `BlackboardKeys.boardKey(BoardId)` or the existing `boards.board_key` SQLite column through your trusted development tooling. The key includes project, thread and board task identity. Authorizing a key does not copy any runtime records into development notes.

In the Java terminal, set environment variables without committing or printing secrets:

```powershell
# Inject a securely generated token into this process environment.
# SUPERMAS_OBSERVATION_TOKEN must contain at least 32 random characters.
$env:SUPERMAS_OBSERVATION_BOARDS = '<authorized-64-hex-key>,<another-authorized-key>'
./scripts/start-observation.ps1 -Database runtime/blackboard.db
```

The Java launcher builds the runtime classpath and starts `ObservationServer` at `127.0.0.1:8091`. It does not initialize a missing database. An older database must first be opened by the updated application's normal `SqliteBlackboardStore` initialization to add `attempts_by_board_revision`; this additive index does not change board versions. The observer requires that index and never creates it: missing index returns 503 instead of falling back to repeated full-table scans. Tokens are mandatory; the process fails closed if configuration is missing. Board authorization is fixed for the process lifetime. Restart to change it.

Supply an extracted Grafana 13.1.2 Windows distribution yourself; the scripts do not download software or install a system service. Explicitly install the pinned plugin into the ignored runtime directory using that distribution's CLI:

```powershell
./tools/grafana/bin/grafana.exe cli --pluginsDir ./runtime/grafana/plugins plugins install yesoreyeram-infinity-datasource 4.1.6
```

In a second terminal, inject the same `SUPERMAS_OBSERVATION_TOKEN` and a strong `GF_SECURITY_ADMIN_PASSWORD` into the process environment, then run:

```powershell
./scripts/start-observation-grafana.ps1 -GrafanaHome ./tools/grafana
```

`tools/grafana` is an example external distribution location, not a tracked dependency. Prefer an external directory when extracting it, or add its local location to `.git/info/exclude`. The launcher stores Grafana data/plugins/logs under ignored `runtime/grafana`, uses the versioned provisioning files, and binds Grafana to loopback. Open `http://127.0.0.1:3000`, sign in, and open **SuperMAS / SuperMAS Blackboard Workflow**. Select an authorized board, an expert (or All), and a time range. Refresh defaults to 15 seconds. Do not enable query caching or anonymous dashboard sharing for this data source.

Optional diagnostics: `SUPERMAS_OBSERVATION_DIAGNOSTICS=true` on the **Java** process enables up to 512 characters of stored reasons and failure messages. Enable only for synthetic/approved data: these uncontrolled strings can contain mail excerpts or credentials. Default mode returns a redaction marker and SHA-256 fingerprint instead. Email subject/body, full prompts, evidence/excerpt text, draft body/explanation, feedback text, frozen configurations and raw lease tokens are never projected, even with diagnostics enabled. Identifiers and relationship metadata remain visible to the authorized developer.

### Docker synthetic validation stack

`observability/grafana/compose.yaml` supplies a local **synthetic validation stack**, separate from Windows-hosted application data. The Grafana base image and Java image are pinned by digest; Infinity 4.1.6 is installed explicitly in the Dockerfile. The earlier documentation-only 3.11.3 pin was rejected by the actual distribution catalog and is superseded. The resolved Java runtime is Temurin 17.0.21+9.

The fixture job runs three controlled workflows through the real leased coordinator: multiple contributors, fallback, and transient failure followed by feedback/revision. It seals its own SQLite fixture in rollback-journal mode for a read-only mount, and a marker prevents repeat startup from rewriting it. This fixture is not a live copy of a Windows application database. Test classes/dependencies are used only by this validation stack.

The API and Grafana share a container network namespace (`network_mode: service:observation`). API port 8091 stays on **container loopback**, with no host publication. Only Grafana is published as `127.0.0.1:3000:3000`. Grafana binds all interfaces inside that namespace to support the host publication; this does not widen the API bind address. It has no database mount for the application fixture. The API mounts that volume read-only and runs with a read-only root filesystem; a size-limited executable tmpfs supports SQLite JDBC's bundled JNI library. No host network, Docker socket, privileged mode or Windows security setting is required.

Inject `SUPERMAS_OBSERVATION_TOKEN` and `GF_SECURITY_ADMIN_PASSWORD`, or supply a protected local environment file containing those two keys. The file must stay under ignored runtime storage and be readable only by its intended local operator. The current local instance uses `runtime/observation-docker.env`; never paste its contents into logs or documentation. Environment values take precedence over the file.

```powershell
./scripts/start-observation-docker.ps1 -EnvironmentFile runtime/observation-docker.env
python ./scripts/verify-observation-grafana.py --fault-tests
```

The start script compiles Java/test fixtures and copies dependencies before Compose builds/recreates the stack. Recreating both namespace-sharing services avoids retaining an obsolete network namespace; the sealed database and Grafana data volumes persist. Docker Desktop must use its Linux engine. Open `http://127.0.0.1:3000/d/supermas-blackboard` and sign in as `admin` with the injected password. For an existing sealed fixture, choose a time range around its stored execution times (for example Last 24 hours the following day), rather than assuming all events happened in the last hour. Current overview/graph data remain current regardless of the event range. Narrow the range to inspect sub-second controlled executions. Grafana can turn auto-refresh off for absolute historical ranges; its toolbar is authoritative. Select a refresh interval explicitly when observing live current ranges. Board discovery is cached on dashboard load; reload after a discovery outage or empty-list test to repopulate its options.

The verification script requires Python 3 and uses its standard library only. It sends actual queries through Grafana/Infinity and compares field values, row counts and revisions against the read-only API for all three boards. It also checks expert filtering, 120-second stale captures, zero authorized-board discovery, 503 failure without successful stale frames, and recovery. This checks the backend contract, not browser rendering.

`ObservationFixtureServer` is a test-only wrapper around the same source/server. Its read-only `runtime/observation-controls` mount provides explicit fault controls: `age-seconds` offsets capture time, `empty-discovery` returns an empty board list, and `unavailable` returns a controlled error. These controls never modify SQLite. Diagnostic text is enabled only because this stack uses synthetic data. Do not repurpose this harness for real mail. The verification script refuses pre-existing controls and cleans up only its own controls in `finally`.

To stop this stack while preserving its data:

```powershell
docker compose --env-file runtime/observation-docker.env -f observability/grafana/compose.yaml stop
```

Restart namespace-sharing services together. Compose declares Grafana's restart dependency on observation; after an engine-level restart or namespace replacement, use the start script to recreate both services. Do not delete volumes to fix a startup issue. Container localhost is not the Windows host: this stack does not connect directly to a native Windows API. Observing that application remains supported through the native same-host deployment above, or requires a separately designed colocated application deployment.


## API and consistency

All requests require `Authorization: Bearer <token>`. Only GET is accepted. No CORS is enabled. Unknown or unauthorized boards return the same 404. Invalid/duplicate query parameters return 400. Missing authentication returns 401, mutation methods 405, storage/projection/size failures 503. Errors never include exception messages or cached successful data. All responses use `Cache-Control: no-store, max-age=0`.

- `GET /boards`: `boards: [{key}]`, only existing authorized keys, at most 100. No mailbox labels or mail subjects.
- `GET /view?board=<key>&agent=<id-or-*>&from=<epoch-ms>&to=<epoch-ms>`: one captured board, with `overview`, `experts`, `events`, `timeline`, `nodes`, `edges` arrays. Agent defaults to `*`. Time defaults to the full range. Time filtering applies to events/intervals; overview, expert summary and graph reflect the current captured state, as labeled in the dashboard. Filter values must be URL encoded.

There is intentionally no pagination cursor across a moving database. Each view is bounded and complete for its documented window, or fails explicitly. It retains the latest 128 immutable revisions, at most 1,000 attempts and 1,000 rows per output section. Limit violations return 503, never a silently partial successful result. Snapshot payloads are limited to 4 MiB each, cumulative captured payloads to 16 MiB, serialized response to 2 MiB. If attempts/graph/current state exceed limits, narrowing time may not help; a future revision-pinned pagination/retention API is needed. Do not increase polling frequency to compensate.

One short deferred read transaction captures database identity, board revision, immutable history and attempts. It rolls back and closes before JSON decoding, graph construction or HTTP delivery. Attempts are constrained to the captured revision. SQL uses indexed board/revision predicates; reads never call `recoverExpired`, `resolveLease`, heartbeat, acknowledge or store initialization. WAL readers do not take a writer transaction, but reading 128 snapshots per panel has a cost. No connection or transaction survives between requests. The server uses two workers and a bounded queue. This is a small local-development interface, not a benchmarked large-board service. SQLite may need its normal WAL/shared-memory file access; this is not an immutable copied-file reader.

**Each HTTP view is internally consistent; separate Grafana queries are not one atomic refresh.** Every row carries database identity, board key, source revision and observation time. Inspect these when comparing panels. Node and edge queries may race and temporarily show different revisions; refresh and compare their detail fields. The API does not claim a cross-query snapshot lease. Node/edge identifiers are stable across refreshes; attempts and draft versions remain distinct.

## Field dictionary

All timestamps are UTC ISO-8601 strings unless marked epoch milliseconds. Counts are integers; scores/ratings are unitless. Null means the value is not recorded or cannot be uniquely established; `UNKNOWN`, `NOT_EVALUATED`, and normal empty arrays are intentional, not failures.

| Fields | Source and meaning |
| --- | --- |
| `databaseId`, `board` | SQLite metadata identity and board key; scope isolation includes project, thread and board task |
| `sourceRevision`, `committedAt` | Captured current `boards.revision` and that immutable snapshot's commit time; a quiet board can be old while observations are fresh |
| `observedAt`, `observedAtMs` | Read/decode completion time from observer clock; not the application event time or last application progress time |
| `historyTruncated`, `historyFromRevision` | Whether older immutable history was omitted by the 128-revision window, and the earliest retained revision |
| `status`, `draftVersion` | Current snapshot board status and maximum stored draft version, 0 when absent |
| `taskCount`, `failedTaskCount`, `failedAttemptCount` | Current tasks, current FAILED tasks, and all recorded FAILED execution records respectively; recovery failures and retries are separate |
| `mailboxApproval`, `mailboxSending` | Always UNKNOWN; Electron actions are not assumed to have returned to Java |
| `collectionStatus` | COLLECTED means this HTTP capture completed, not that the board is healthy or the displayed response is still fresh |
| `agent`, `currentExecution` | Agents known from task/draft/assessment/contribution records; RUNNING_RECORDED if a current task is RUNNING, otherwise PENDING if pending work exists, otherwise UNKNOWN. This does not prove lease validity or that a worker is alive. Unrecorded registered agents cannot be enumerated |
| `assessment`, `score`, `assessmentSource`, `reason` | Latest stored control To-Do estimate/eligibility per agent, falling back to routing candidates. SUITABLE_AT_ASSESSMENT / NOT_SUITABLE_AT_ASSESSMENT describe the past strategy activation, not model skill or execution. Older decisions/routing without `eligible` use SCORED_ELIGIBILITY_UNKNOWN; absent estimates use NOT_EVALUATED. Reasons default to a redaction fingerprint |
| `contributionCount` | All unique structured `Contribution.id` values by validated author in the current snapshot, including revisions; promoted copies counted once. A contribution can exist despite a later execution failure |
| `usedByComposer`, `requestAnswered` | UNKNOWN; having contributed, declaring coverage, being readable by composer and answering correctly are different facts |
| `lastExecutionResult`, `lastDurationMs`, `lastFailureKind`, `lastFailureReason` | Last stored execution for this agent's tasks, with duration in milliseconds and recorded failure fields; historical result, never current execution state |
| `events.id`, `kind` | Stable board-scoped execution index, control round or cancelled-task identifier; EXECUTION, CONTROL or TASK_STATUS |
| `time`, `eventTime` | EXECUTION: actual recorded `finishedAt` in epoch ms and ISO form. CONTROL/cancellation: first retained snapshot commit containing it, with eventTime=null; this can be a later observation boundary if older history was truncated. Control provenance is deliberately not treated as decision time because current coordinator uses mail receipt time |
| `firstObservedRevision` | First retained revision containing control/cancellation. Snapshot time does not prove an exact earlier event time |
| `task`, `attempt`, `action` | Stored task/action; attempt is SHA-256 of frozen token only when task+start uniquely matches an attempt. Ambiguous attempt joins are UNKNOWN. Control has no durable task/attempt foreign key, so these are UNKNOWN; cancellation without an attempt uses NONE |
| `focus`, `rating` | Stored ControlEntry focus and selected rating |
| `result`, `durationMs`, `failureKind`, `failureReason`, `retryable` | Stored execution result, finish-start milliseconds, failure classification, redacted message and recorded retry policy; control result SELECTED is not completion. Cancelled pending tasks have no invented execution duration |
| `modelCallResult`, `taskCompletionAccepted`, `outputAccepted` | Model result UNKNOWN; a persisted execution proves its task completion/recovery record was accepted, not model success or acceptance of any specific output. Output acceptance remains UNKNOWN; drafts/contributions present in the snapshot separately prove their storage |
| `timeline.start`, `end`, `state`, `task`, `tasks` | Epoch-ms intervals derived from recorded execution bounds and adjacent immutable commits. Each expert is split at actual recorded boundaries into disjoint chronological segments; contradictory PENDING/RUNNING coverage is OVERLAPPING_RECORDS, not a guessed state. `tasks` lists supporting task IDs; `task` is null when several tasks support a segment. Missing/open/zero-duration bounds produce gaps; terminal states remain point events |
| `nodes.id`, `title`, `subTitle` | Board-prefixed stable entity ID, metadata label, entity type/review state. Attempts use hashed tokens; draft IDs include version. Node titles contain no mail or evidence text |
| `edges.id`, `source`, `target`, `mainStat` | Deterministic edge hash, scoped endpoints and explicit relation. Authorship comes from Contribution/Draft author; coverage from Contribution.covers to recorded requests; excerpt declarations require referenced evidence IDs present; agent-to-attempt from frozen attempt metadata. No chronological dependency, semantic entailment or contribution-to-draft adoption is inferred |

`ControlEntry.Rated.eligible` is a nullable additive field. New AgendaStrategy decisions persist the already computed eligibility; historical `control/v1` JSON without the field still decodes with null. The routing/model/coordinator execution algorithm is unchanged. The historical six-argument constructor remains available.

## Dashboard behavior and validation

The dashboard has an authorized-board count (0 = NO BOARD DATA), collection-age stat, current overview table, expert assessment/current-work table, native State timeline, control/execution/cancellation table and native Node graph. With no existing authorized boards, discovery succeeds with zero; select a valid board before interpreting the other panels' query errors. Timeline metadata is available in the query frame before display transformations; graph revision metadata is exposed as detail fields. A 15-second refresh produces several independent reads; use Query inspector to check versions and costs. Overview and current summaries do not rewind when changing the event time range.

Age is computed by backend JSONata using current backend time minus `observedAtMs`: >=45 seconds is warning, >=90 seconds is stale. An empty successful array is a normal empty result; missing collection data shows gray UNAVAILABLE; transport/auth/storage failures appear as Grafana query errors. No success is synthesized on a failed HTTP response. Grafana can retain old panel values when a query fails: a query-error indicator and the last observation timestamp take precedence over retained values. A paused dashboard/browser cannot continuously recompute freshness; inspect the last observation time and refresh before relying on it. The Docker browser follow-up below verified the error overlays, age colors, variable interpolation and interval layout. Grafana lazily queries panels as they enter the viewport; offscreen retained rows must not be treated as a fresh independent capture.

Reproduce local code/configuration checks:

```powershell
mvn -B -ntp '-Dtest=ObservationTest,ObservationWorkflowTest,ObservationDashboardTest,BlackboardCollaborationTest,LeasedCoordinatorTest,BlackboardTest,BlackboardPrototypeChangesTest' test
mvn -B -ntp -DskipTests package
```

On 2026-11-07, the selected command passed **57 tests, 0 failures, 0 errors, 0 skipped**: 17 observation tests plus 40 existing blackboard/coordinator/collaboration regressions. The observation tests use synthetic mail, SQLite and controlled agents with fixed output, covering multiple specialists, fallback, transient failure/retry, rejection/revision, recorded lease recovery and abandonment, cancellation, exact board isolation, expired-lease polling without writes, redaction, auth/method/query failures, capture bounds, concurrent reads, indexed attempt queries, successful empty discovery and comparison with authoritative snapshots. The workflow test uses the actual prototype `LeasedCoordinator` and `AgendaStrategy`; it does not turn the prototype into a production scheduler. Model success is deliberately unknown despite the fixed draft fixture. This is a targeted run, not a full Maven-suite or hosted-CI claim. PowerShell syntax and JSON/static dashboard contract checks passed.

UI validation checklist for an equipped host: install the pinned plugin explicitly; start both processes; select two authorized boards and verify isolation; compare panel counts/revisions with API/SQLite; verify one timeline row per expert and graph endpoints/versions; produce empty time windows; stop Java and confirm visible query errors; restart with a stale test capture and check age thresholds; pause refresh and confirm the visible last observation time is understood. Do not call a generated JSON file a completed UI validation.

The separate `mvn -B -ntp -DskipTests package` build and PyYAML parsing of both provisioning files also passed on 2026-11-07.

Docker follow-up on 2026-11-08: Docker Desktop, Grafana 13.1.2 and Infinity 4.1.6 ran successfully. Provisioning and every panel query passed against all three sealed synthetic boards. Actual browser checks cover board/expert filtering, overview values, disjoint timeline rows with pending/running colors, relationship rendering, empty time windows and empty discovery, red 120-second stale captures, gray UNAVAILABLE/error indicators for controlled 503 and a genuinely paused API container, and recovery. Local screenshots remain under ignored runtime storage; no runtime records are published as development knowledge. The final 20 observation tests passed (0 failures/errors/skips), following 40 existing regression tests; the earlier combined run had 58 tests before the two interval regressions were added. The repeat-start script preserved the fixture database byte-for-byte. Native interval ends insert nulls in Grafana 13.1.2, so overlapping lease-start/commit boundaries are split and marked instead of letting one record erase the next running interval. Native Windows Grafana, real mailbox/model integration, large-board load and hosted CI remain unverified. JavaFX remains independent. Some coordinator early exits still lack stored ControlEntry records, and drafts lack structured adopted-contribution receipts; the observer does not invent them.
