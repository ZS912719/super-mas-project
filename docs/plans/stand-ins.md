# Stand-Ins

These classes exist only because the Metadata and Context agents need something to run against. Each belongs to a teammate's area. Search for `PROTOTYPE-STAND-IN` to find them all, then replace them with the real implementation.

| Class in `org.supermas.prototype` | Owning area | Behaviour our agents rely on |
| --- | --- | --- |
| `InMemoryBlackboard` | Blackboard | One isolated board per task. Agent snapshots are filtered by evidence permissions. Contributions from another scope or unauthorized source are rejected |
| `InMemoryBlackboardFactory` | Blackboard | A new board with a unique task identifier for each email |
| `Capabilities` | Agent configuration | A capability that separates agents able to reply from context agents |
| `InMemoryAgentRegistry` | Agent registry | Registration through configuration only. `replyCandidates()` leaves out context agents |
| `ScriptedCoordinator` | Coordinator | Preprocessing on receive, one action per round, restricted board access for agents, enforced budgets. Optionally records each received email and each new draft through `ConversationRecorder` (P10); a history failure is recorded on the board and does not stop the email |
| `ScoreRoutingStrategy` | Routing | Clear, ambiguous and absent matches, with candidates and reasons kept |
| `DraftFirstCoordinationStrategy` | Scheduling | The next action is chosen from board state |
| `ContextFirstCoordinationStrategy` | Scheduling | Each selected context agent runs once before routing, then further context agents that fit (such as the discovered-topics agent), then the missing-information check, then one pending further retrieval, keeping rounds back for the reply |
| `TemplateGenerationService` | LLM integration and final email writing | `generate(request, config)` returns exactly one of a draft or a failure. This stand-in writes a fixed template from the reply plan, with no model |

## Known gaps for the real implementation

- `Blackboard` cannot record feedback or approval, so revision after rejection does not run.
- `ScriptedCoordinator` carries out a pending task of the scheduled action, but only `ContextFirstCoordinationStrategy` schedules them, and only for further retrieval. Other suggested tasks are stored and not run.
- There is no wall-clock timeout around agents.
- No real `GenerationService` or `LocalLlm` adapter exists. Role agents build their own `ModelRequest` and call only `generate`; `prepare` is unused by them.
- Nothing calls `ConversationRecorder.onSentConfirmed`, because there is no mail transport.
