# Stand-Ins

These classes exist only because the Metadata and Context agents need something to run against. Each belongs to a teammate's area. Search for `PROTOTYPE-STAND-IN` to find them all, then replace them with the real implementation.

| Class in `org.supermas.prototype` | Owning area | Behaviour our agents rely on |
| --- | --- | --- |
| `LeasedCoordinator` | Coordinator | The `ScriptedCoordinator` behaviour on the teammate's durable `DefaultBlackboard` (SQLite), unchanged: every action and every routing assessment is a task the agent claims under a lease and completes; execution records come from the board; boards reopen from the store after a restart. Used by `PrototypeApp` and `DurableEndToEndTest` (step 11.2) |
| `EvidencePromotion` | Coordinator | Agents submit evidence private to themselves, as the durable board requires. After a context agent's task succeeds, copies go to agents whose trusted configuration lists the source, narrowed to the document's readers in the index; inferences only from their author; nothing from failed tasks or reply agents (step 11.3) |
| `control.AgendaStrategy` (not a stand-in since Phase 12; this member's design for the personal prototype) | Scheduling and routing | BB1-style controller: estimates through read-only views, To-Do-Set rated within the focus level, relevant-event policy, fallback, control decisions on the board (step 12.4) |
| `BoardReviewService` | Human review | Reject with feedback on the durable board, so revision runs; edit and approve stay in the mailbox (step 12.6) |
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
| `llm.OpenAiCompatibleLlm.online` (in `llm`, selected by `LlmSettings` provider `online`) | Local LLM integration | A development stand-in for the local engine: OpenRouter over HTTPS with a key from the environment. Off by default; the agent service needs a second opt-in. Replace with provider `local` before delivery (step 13) |

The teammate's `DefaultBlackboard`, `DefaultBlackboardFactory` and `SqliteBlackboardStore` (from `codex/blackboard-storage`) are the real blackboard. `InMemoryBlackboard` and its factory remain only so the unit tests stay fast.

## Known gaps for the real implementation

- Feedback is recorded since step 12.6 (`Blackboard.recordFeedback`), so revision runs on the blackboard. Approval and edits are still not on the board: the durable board refuses `setStatus(APPROVED)`, and the mailbox does approval.
- Fixed in step 12.1 (teammate's code, prototype branch): `BoardState.transition` keeps the information need; drafts need `draft-reply` or `compose-reply`.
- Leases are not renewed by heartbeat; a task longer than the 5-minute prototype lease would be fenced.
- `ScriptedCoordinator` carries out a pending task of the scheduled action, but only `ContextFirstCoordinationStrategy` schedules them, and only for further retrieval. Other suggested tasks are stored and not run.
- There is no wall-clock timeout around agents.
- `LlmGenerationService` with `OpenAiCompatibleLlm` is the real generation path (step 12, step 13), tested only against fake engines so far. Role agents build their own `ModelRequest` and call only `generate`; `prepare` is unused by them.
- Nothing calls `ConversationRecorder.onSentConfirmed`, because there is no mail transport.
