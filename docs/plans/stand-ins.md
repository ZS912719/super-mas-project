# Stand-Ins

These classes exist only because the Metadata and Context agents need something to run against. Each belongs to a teammate's area. Search for `PROTOTYPE-STAND-IN` to find them all, then replace them with the real implementation.

| Class in `org.supermas.prototype` | Owning area | Behaviour our agents rely on |
| --- | --- | --- |
| `LeasedCoordinator` | Coordinator | The `ScriptedCoordinator` behaviour on the teammate's durable `DefaultBlackboard` (SQLite), unchanged: every action and every routing assessment is a task the agent claims under a lease and completes; execution records come from the board; boards reopen from the store after a restart. Used by `PrototypeApp` and `DurableEndToEndTest` (step 11.2) |
| `EvidencePromotion` | Coordinator | Agents submit evidence private to themselves, as the durable board requires. After a context agent's task succeeds, copies go to agents whose trusted configuration lists the source, narrowed to the document's readers in the index; inferences only from their author; nothing from failed tasks or reply agents (step 11.3) |
| `AgentService` (wiring, proposal) | Mailbox-to-coordinator integration | Loopback `POST /v1/reply` for the desktop mailbox: draft, routing, plan, and a classification on clear matches. Contract in `docs/plans/agent-service-api.md` (step 11.6) |
| `PrototypeApp` (wiring, not a stand-in of one part) | Whole team | Runs `.eml` files through every part to drafts awaiting review on SQLite, with Markdown for Obsidian (step 11.4) |
| `InMemoryBlackboard` | Blackboard | One isolated board per task. Agent snapshots are filtered by evidence permissions. Contributions from another scope or unauthorized source are rejected |
| `InMemoryBlackboardFactory` | Blackboard | A new board with a unique task identifier for each email |
| `Capabilities` | Agent configuration | A capability that separates agents able to reply from context agents |
| `InMemoryAgentRegistry` | Agent registry | Registration through configuration only. `replyCandidates()` leaves out context agents |
| `ScriptedCoordinator` | Coordinator | On `InMemoryBlackboard`, kept for fast unit tests. Preprocessing on receive, one action per round, restricted board access for agents, enforced budgets. Optionally records each received email and each new draft through `ConversationRecorder` (P10); a history failure is recorded on the board and does not stop the email |
| `ScoreRoutingStrategy` | Routing | Clear, ambiguous and absent matches, with candidates and reasons kept |
| `DraftFirstCoordinationStrategy` | Scheduling | The next action is chosen from board state |
| `ContextFirstCoordinationStrategy` | Scheduling | Each selected context agent runs once before routing, then further context agents that fit (such as the discovered-topics agent), then the missing-information check, then one pending further retrieval, keeping rounds back for the reply |
| `TemplateGenerationService` | LLM integration and final email writing | `generate(request, config)` returns exactly one of a draft or a failure. This stand-in writes a fixed template from the reply plan, with no model |

The teammate's `DefaultBlackboard`, `DefaultBlackboardFactory` and `SqliteBlackboardStore` (from `codex/blackboard-storage`) are the real blackboard. `InMemoryBlackboard` and its factory remain only so the unit tests stay fast.

## Known gaps for the real implementation

- `Blackboard` cannot record feedback or approval, so revision after rejection does not run. The durable board refuses `setStatus(APPROVED)` until a review service exists.
- `BoardState.transition` on the durable board drops a task's `InformationNeed` when it is claimed or completed. `LeasedCoordinator` hands the original task to the agent, so the agent still gets it, but the board's history does not. A one-line fix for the teammate (step 11.2-11.3 report).
- The durable board does not check the drafting capability (P5): any agent holding a lease could submit a draft.
- Leases are not renewed by heartbeat; a task longer than the 5-minute prototype lease would be fenced.
- `ScriptedCoordinator` carries out a pending task of the scheduled action, but only `ContextFirstCoordinationStrategy` schedules them, and only for further retrieval. Other suggested tasks are stored and not run.
- There is no wall-clock timeout around agents.
- No real `GenerationService` or `LocalLlm` adapter exists. Role agents build their own `ModelRequest` and call only `generate`; `prepare` is unused by them.
- Nothing calls `ConversationRecorder.onSentConfirmed`, because there is no mail transport.
