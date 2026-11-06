# Blackboard Storage (teammate) and Blackboard Control (Phase 12)

**Packages:** `org.supermas.blackboard.*` (teammate), `org.supermas.control`, `org.supermas.prototype` (coordinator, promotion, review). **Docs:** `docs/blackboard-runtime.md`, `docs/blackboard-validation.md`, `formal/README.md` (teammate); `docs/plans/blackboard-control.md`; reports 11.2-11.3, 12.

## Storage (teammate)

SQLite is authoritative: one board per email, every committed revision kept as an immutable JSON snapshot. Agents write only under a **task lease** with a fencing token, inside short `BEGIN IMMEDIATE` transactions; model calls happen outside transactions; expired leases are recovered. Markdown generations are published atomically for Obsidian and audited against the database. Task and projection protocols are checked with nuXmv models. Agent contributions must stay private to their author until the coordinator promotes them.

Changes made on the prototype branch (step 12.1, approved by the project owner): read-only `viewFor(AgentConfig)` without a lease, `recordFeedback`, drafting capability check, information need kept on claim, feedback visible to agents, `Action.CONTRIBUTE`.

## Control (Phase 12)

| BB1 concept | Implementation |
| --- | --- |
| Domain blackboard levels | email · requests · context · contributions · reply |
| Knowledge sources | Request splitter, context agents, specialists, composer: `assess` (estimate) + `execute` (action) |
| KSAR, To-Do-Set | Every source with a non-zero estimate this cycle, rated `estimate × weight(focus)` |
| Scheduler, Chosen-Action | `AgendaStrategy.next` picks the highest rating; `LeasedCoordinator` runs it under a lease |
| Problem | One proposed reply answering every request; solved when the latest draft is proposed |
| Strategy, Focus | Successive levels; focus = first level with an eligible activation; budget can force the reply level |
| Policy | Context and request sources re-run only on relevant events (feedback, pending further retrieval); contribution threshold 0.4; reserve rounds |
| Control blackboard | `control/v1` entries per round, readable by no agent, visible in Markdown; `selectionReason` per execution |

Corkill's split: sources give cheap estimates through read-only views; the controller has no email knowledge (levels come from capabilities in configuration).

## Choices and alternatives

| Choice | Alternative | Why |
| --- | --- | --- |
| **SQLite with leases and fencing** (teammate) | In-memory board | Durable, restart-safe, auditable; concurrency defined precisely |
| **Agenda controller after BB1** | Scripted pipeline (Phase 11), contract net | Control chosen from board state and explained; agents stay anonymous |
| **Strict levels + relevant events** | Rate across all levels; re-run on any change | Both tried; they wasted rounds or lost context (step 12 report) |
| **Lease-free read views for estimates** | Estimates under leases | One estimate per source per cycle would have been a task and a database write each |
| **Control entries on the board** | Logs outside the board | Explanation travels with the board and its Markdown view |

## Why it complies

- **Statement §14.4-7, §20:** communication only through the board; several agents collaborate on one response; decisions are logged and explainable; confidentiality by private contributions and promotion.
- **Targeted architecture:** BB1's domain and control parts, knowledge sources, KSARs, scheduler and control plan levels are all present; the one gap is that the control plan is fixed in code instead of being built by control knowledge sources.

## Open points

Parallel agents on one board (their input validation is board-wide), lease heartbeats for slow models, control knowledge sources, retention of old revisions.
