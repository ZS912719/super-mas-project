---
id: team-blackboard
type: implementation-summary
scope: team-development
updated: 2026-11-05
status: feature-branch
source_ref: d58b69c
---

# Blackboard Storage and Observation

Implemented on `codex/blackboard-storage` at the inspected revision, absent from the inspected `main` baseline. Source: feature-branch `docs/blackboard-runtime.md`, `docs/architecture.md` and `docs/blackboard-validation.md`.

## Ownership

Under `org.supermas.blackboard`: `api` holds contracts; `impl` implements storage-independent board/factory logic; `model` holds version/lease records and an encapsulated state editor; `persistence` defines storage; `persistence.sqlite` adapts SQLite; `persistence.codec` handles JSON/digests; `export` publishes Markdown; `app` wires demo/observer adapters. There is no general-purpose Java `runtime` package. Blackboard and projector depend on `BlackboardStore` rather than the SQLite adapter.

## State and execution protocol

SQLite is authoritative, using WAL, foreign keys, bounded busy waiting, full synchronous mode and short `BEGIN IMMEDIATE` write transactions. Model/tool calls stay outside transactions. Durable task leases are separate from database writer locks.

Claims persist a unique fencing token, frozen execution configuration, input revision and lease. Contributions and completion validate active ownership and expected revision. Expired or abandoned attempts are fenced; `RETRY_SAFE` may requeue while uncertain external effects require confirmation. The safe-retry flag is a caller assertion, not proof that a tool is idempotent. Exactly repeated completion can return a durable receipt; arbitrary contributions do not have equivalent replay guarantees.

An unrelated business revision on the same board currently invalidates attempt inputs. This favors conservative safety over throughput. Specialist views filter evidence and expose neither global scheduling nor approval/sending authority. Trusted coordinator/store handles are API boundaries, not an OS sandbox.

## Persistence and projection

Tables cover database identity, current boards, immutable snapshots, frozen attempts, active leases, completion receipts and desired/acknowledged projection watermarks. General migrations and retention are deferred.

The projector captures one committed revision, writes a complete immutable generation, atomically publishes `current.md`, then acknowledges in SQLite. A vault lock coordinates publishers only. Database identity prevents mixing sources. Audit compares document content to historical state, not merely to a self-reported hash. A faithful old generation may still be behind the database; exporter recovery eventually catches up under stated assumptions.

The application vault is `runtime/super-mas-vault`, with human-authored `knowledge/` and generated board generations. Knowledge edits do not automatically update SQLite or agent configuration. The observer recovers leases and refreshes exports; it does not execute agents.

## Limits

No complete coordinator, specialist runner, Java retrieval/model service, human-review implementation or mailbox connection. No exactly-once external effects, arbitrary power-loss proof, hostile-local-process defense, multi-host database deployment or bounded Obsidian UI freshness. Old generations and snapshot history currently require a future retention policy.

Entry points and validation: [Source-Index](Source-Index.md), [Build-CI-and-Delivery](Build-CI-and-Delivery.md), [Validation-and-Limits](Validation-and-Limits.md).
