# Local Blackboard Runtime

## Authority and scope

The `org.supermas.blackboard` subpackages implement the first durable blackboard slice on Java 17. SQLite contains authoritative state. Markdown is a disposable projection. Obsidian displays the vault and allows manual knowledge editing; it does not schedule tasks, own locks or update SQLite.

## Source packages

| Package below `org.supermas.blackboard` | Responsibility |
| --- | --- |
| `api` | Blackboard/view/factory/agent/preprocessor contracts and transition conflict errors |
| `impl` | `DefaultBlackboard` and `DefaultBlackboardFactory`; depend on the storage interface |
| `model` | `BlackboardData` records and `BoardState`, an encapsulated transaction-local editor |
| `persistence` | `BlackboardRepository`, atomic `BlackboardStore`, stable `BlackboardKeys`, storage errors |
| `persistence.sqlite` | `SqliteBlackboardStore`, the concrete SQLite adapter and JDBC transactions |
| `persistence.codec` | `JsonCodec`, shared deterministic JSON rendering and digests |
| `export` | `MarkdownProjector`, generation publication, audit and retry |
| `app` | `BlackboardDemo` and `BlackboardObserver`; wires concrete adapters |

Contracts do not import implementations. The blackboard and exporter depend on `BlackboardStore`, while SQLite implements it. Shared mutable editor fields remain private; callbacks receive explicit state operations instead of direct field access. Storage callbacks are coordinator-side facilities and must never be passed to specialists.

Tests mirror the same responsibilities: `blackboard.impl.BlackboardTest` covers task/storage integration, `blackboard.export.MarkdownProjectorTest` covers projection, and `blackboard.testing` contains shared fixtures. The `runtime` data directory below is application data, not a Java source package.

The mailbox remains a separate application. No coordinator strategy, specialist implementation, real model invocation, knowledge retriever, human-review service or mail transport integration is introduced here. The demo stores a fixed demonstration draft and never sends a message.

## Entry points

From the repository root, with Java 17+ and Maven available:

```powershell
./scripts/run-blackboard.ps1 -Mode demo
./scripts/run-blackboard.ps1 -Mode audit
./scripts/run-blackboard.ps1 -Mode watch
```

The demo creates a fresh board on each invocation, claims a task, stores a proposed draft, completes the task and exports observations. `audit` exits unsuccessfully for invalid or lagging documents. An empty database has no observations to check. `watch` checks expired leases and regenerates Markdown every second; Ctrl+C stops it. It does not execute pending tasks. All modes accept `-DataDirectory` (default `runtime`). Maven resolves the runtime classpath on first use.

Open `runtime/super-mas-vault` as an existing vault in Obsidian. Under `generated`, open a board's `current.md`, then follow its snapshot link. `knowledge` is for human-authored material. Editing it has no automatic effect on the runtime; callers provide trusted `AgentConfig` values when claiming work. Generated files must never be used to drive agent execution.

```text
runtime/
|-- blackboard.db
|-- blackboard.db-wal             # May exist while connections are active
|-- blackboard.db-shm
`-- super-mas-vault/
    |-- knowledge/
    `-- generated/
        |-- .source-id
        `-- <hashed-board-id>/
            |-- current.md
            `-- generations/
                `-- <revision>-<generation-id>/
                    |-- overview.md
                    `-- manifest.json
```

Runtime data, prompts and email bodies are private local data, ignored by Git. Do not put the live database on a network share or under active cloud synchronization. Knowledge-only sync or offline backup can be designed separately. Local code holding the store/coordinator handle is trusted: specialist capabilities are API boundaries, not an operating-system sandbox.

## API usage

`DefaultBlackboardFactory.create` persists an initial board. `open` reads an existing identity. `restore` and the legacy repository `save` are insert-only imports; they cannot replace an existing board or import running tasks without leases. `versionedSnapshot` returns detached state and the committed revision. Historical revisions and frozen execution configurations remain queryable through the store.

```java
SqliteBlackboardStore store = new SqliteBlackboardStore(data.resolve("blackboard.db"));
DefaultBlackboardFactory factory = new DefaultBlackboardFactory(store);
DefaultBlackboard board = factory.create(email, analysis);
board.enqueue(new Task("summarize", board.snapshot().id(), Action.GENERATE,
        Optional.empty(), TaskStatus.PENDING, "Summarize the supplied evidence"));
TaskLease lease = board.claim("summarize", agent, Duration.ofMinutes(1), RecoveryPolicy.RETRY_SAFE);
AgentBlackboard view = board.accessFor(lease);
// Execute retrieval/model work outside the database transaction.
view.submitDraft(new DraftProposal("Subject", "Draft body", agent.id(), "Evidence summary"));
board.complete(lease, TaskStatus.SUCCEEDED, "Draft stored");
```

Import the factory/blackboard from `org.supermas.blackboard.impl`, the adapter from `org.supermas.blackboard.persistence.sqlite`, and the projector from `org.supermas.blackboard.export`. `TaskLease` and `RecoveryPolicy` are nested in `org.supermas.blackboard.model.BlackboardData`; domain types are nested in `org.supermas.model.Domain`. The complete runnable example is `blackboard.app.BlackboardDemo`.

`accessFor(AgentConfig)` intentionally fails: runtime specialist mutations require a current task lease. `recordExecution` cannot fabricate execution records. `updateTask` currently supports only cancelling pending work. `setStatus(APPROVED)` is unavailable because explicit human review is not implemented. These restrictions preserve signatures while preventing unfenced execution through legacy methods.

Specialist views expose the board email/analysis, authorized evidence, their current task, its execution records, their drafts and related feedback. Routing and global failures are omitted. Evidence must have the correct scope, an authorized source and exactly the submitting agent as audience; coordinator promotion is required to broaden access. Agents cannot assign suggested tasks to peers. This remains a coarse board-email access model; separate boards are required when email contents themselves need different access rights.

## Transaction and task protocol

Every connection enables foreign keys, a five-second busy timeout and `synchronous=FULL`. The database uses WAL. A command opens a separate connection, starts `BEGIN IMMEDIATE`, reads and validates state, writes its changes and commits. Exceptions roll back the transaction. The timeout is bounded waiting, not an infinite retry loop. Storage errors expose uncertain outcomes; do not automatically replay non-idempotent commands.

| Command | Validation | Atomic effects |
| --- | --- | --- |
| Claim | Processing board; pending task; compatible agent assignment | Running task, unique execution token, frozen configuration and prompt hash, lease, revision, export request |
| Heartbeat | Active unexpired token; unchanged expected board revision | Renew lease only; do not change the business revision |
| Contribute | Active token; processing board; expected revision; scope/source/author checks | Contribution, next revision, export request, advance this attempt's expected revision |
| Complete | Active token; processing board; expected revision | Terminal task, execution record, delete active lease, completion receipt, revision, export request |
| Recover | Lease deadline reached | Fence attempt; record interruption; requeue safe work or fail uncertain work; revision and export request |
| Abandon | Current unexpired token, including an attempt with stale input | Fence attempt immediately; apply its recovery policy and record interruption |

No write transaction spans a model or tool call. A durable task lease is distinct from the SQLite writer lock. Durations must be positive and at most one hour. Deadlines use the host UTC clock; large clock changes can prematurely expire or prolong leases. Fencing tokens still prevent an expired/replaced attempt from committing after recovery.

Input validation is deliberately conservative: any other business revision on the same board invalidates an executing attempt. This can reduce throughput for independent tasks. `abandon` and reclaim refresh inputs; a later version can track per-resource dependencies. Claiming another task on the same board is itself such a revision. Different boards have separate logical versions, although SQLite still serializes their short writes.

Exact completion retries with the same token, status and summary return the original committed revision, even after lease removal. A different completion payload is rejected. Evidence/draft submission and enqueue do not yet provide retry identifiers; inspect state after an uncertain response instead of replaying them blindly.

The `RETRY_SAFE` policy is an explicit caller assertion, not automatic proof of idempotence. `REQUIRE_CONFIRMATION` recovery produces a failed task and a non-retryable interruption record with a manual-confirmation message. It never reruns work or sends anything. A coordinator must resolve uncertainty and enqueue explicitly authorized follow-up work. Fencing SQLite results cannot undo external side effects or guarantee exactly-once tool execution.

## Persistence layout

| Table | Purpose |
| --- | --- |
| `metadata` | Persistent database identity |
| `boards` | Current revision by hash of the full project/thread/board identity |
| `snapshots` | Immutable JSON state for every committed revision |
| `attempts` | Immutable claim inputs, including exact trusted configuration and system prompt |
| `leases` | Active execution tokens, renewed deadline, expected revision |
| `receipts` | Idempotent terminal requests and their original result revision |
| `projections` | Desired and acknowledged export revision, updated with the business transaction |

Schema version 1 is initialized transactionally. Newer versions are rejected. General schema migration and history retention are deferred. Storage grows with snapshot history; it is suitable for the first local implementation, not indefinite high-volume retention.

## Projection and recovery

The desired revision is a transactional outbox watermark. Exporters may coalesce intermediate revisions; historical snapshots remain in SQLite. All content for one generation comes from one committed revision, including only attempts that existed at that revision. Execution tokens and mutable lease deadlines are not exported.

One publisher at a time holds a stable vault file lock. This lock coordinates exporters only; SQLite remains responsible for task concurrency. The vault records its SQLite source identity to reject accidental publication from a different database.

The publisher writes and flushes a complete staging generation, atomically renames it into place, then atomically replaces `current.md`. It acknowledges publication afterward in SQLite. Atomic moves must be supported on the local filesystem; there is no unsafe fallback. The implementation rejects symbolic links in generated paths, but does not defend against a hostile local process changing filesystem structure concurrently.

| Failure window | Result and recovery |
| --- | --- |
| Before SQLite commit | Rollback; no new visible snapshot or export request |
| After commit, before export | Desired revision stays ahead; watcher retries |
| During generation | Old entry remains valid; incomplete staging is unreferenced |
| After generation, before entry update | Old entry remains valid; retry creates a complete generation |
| After entry update, before acknowledgement | New entry is valid; retry audits it and acknowledges without republishing |
| Generated content edited or removed | Audit detects the mismatch; reconciliation creates a replacement generation |

Audit compares actual Markdown to a deterministic projection of the matching historical SQLite revision, in addition to checking manifest identity and hash. Old faithful documents are valid but lagging; strict audit reports both values. It does not assert that a static entry is always live. Old generations and incomplete staging are retained for now; automatic cleanup is deferred. Old files may reflect past state and must not be used as live progress.

Flushed files and atomic replacement cover tested process-crash windows. Directory metadata durability and arbitrary power loss are not proven, and SQLite/filesystem publication is not one distributed transaction. Lost Markdown can be regenerated. The Obsidian cache and rendered screen require separate UI validation; file correctness does not imply the viewer refreshed immediately.

## Verification

```powershell
mvn test
mvn package
./scripts/check-formal.ps1
```

See [the formal models](../formal/README.md), [validation evidence](blackboard-validation.md) and [runtime UML](uml/runtime.puml). No live provider or real model result is claimed. The validation report records the separate initial Obsidian UI check; continuous viewer-cache freshness remains outside the guarantee.
