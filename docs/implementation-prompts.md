# Implementation Prompts

These task-scoped prompts summarize implementation requirements for review and reproduction. They exclude private conversations, credentials and runtime data. Entries reconstructed on 2026-11-08 cover the current Grafana task; they are not contemporaneous execution logs or evidence of completed checks. See [observation documentation](blackboard-observation.md) for actual verification and limits.

## 2026-11-07: Implement blackboard observation

Implement Grafana observation for SuperMASProject. Read AGENTS.md and pinned committed team guidance first; inspect current source, branch, HEAD, remotes and working-tree changes. Preserve unrelated work. Keep SQLite authoritative and Grafana read-only: no scheduling, task claiming, recovery, approval or sending. Reuse immutable snapshots, attempts and control records through short bounded read transactions. Do not rewrite agents, coordinator or model calls. Exclude private content and secrets by default.

Consult current official Grafana documentation and verify any third-party plugin license, maintenance, compatibility, backend querying and deployment needs. Pin actual versions. Use a separate authenticated Java metadata HTTP API and provisioned Grafana dashboards where supported. Expose overview, expert suitability separately from execution, recorded timeline intervals, decision/execution tables and only source-backed graph relationships. Expose source revisions, collection versus event time, gaps, unknown states, limits and cross-query consistency boundaries. Do not infer lease expiry, model success, contribution adoption or mailbox synchronization.

Provide Windows-compatible startup, versioned provisioning and field semantics. Keep API loopback-only; use protected environment configuration for credentials and bounded refresh/query limits. Validate with synthetic mail, controlled agents and fixed responses: multi-expert contribution, fallback, failure/retry, rejected draft revision, read-only polling, board isolation, source-value agreement and stale/failure presentation. Do not use real mail or paid models. Report interface/configuration/UI verification separately. Update ignored local progress and affected shared knowledge with exact source revisions and limits.

## 2026-11-08: Deploy with Docker and inspect Grafana

Use the already installed, running Docker Desktop to deploy and validate Grafana without installing system services or changing security settings. Provide a repeatable synthetic stack with pinned images/plugin, protected secrets and a read-only fixture. Keep the API on container loopback and publish Grafana only on host loopback. Do not mount the live Windows application database. Exercise actual Infinity queries and compare frames with authoritative API values. Reopen the Grafana UI and inspect board/expert selection, overview, timeline, graph, empty ranges, stale data, API failures and recovery. Restore temporary fault controls after verification and record remaining integration limits.

## 2026-11-08: Publish focused commits and shared knowledge

Review only task-related changes, split large modifications into focused Conventional Commits, commit and push them to the existing code branch. Preserve unrelated work. Update affected team knowledge in its separate knowledge-only checkout with the exact published code revision, actual verification and limitations. Fetch/reconcile concurrent changes without force pushing, then publish a separate knowledge commit and verify both remote references and intended knowledge content.

## 2026-11-08: Maintain implementation instructions

Keep this English prompt record versioned with code and update it after each implementation-related user instruction. Keep identifiers, comments, project-authored documentation and commit messages in English. Restrict README content to dependencies, structure, deployment and usage, using relative paths; move project progress into a separate linked document. Record Chinese local progress in an ignored file. Read task-relevant committed team knowledge when needed and update affected notes after code tasks. Split large changes into focused commits. Keep the developer's conversation language preference out of shared AGENTS.md. Reuse the existing team vault; for future long projects without a remote knowledge vault, propose one before creating it.
