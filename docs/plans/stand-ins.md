# Stand-Ins

These classes exist only because the Metadata and Context agents need something to run against. Each belongs to a teammate's area. Search for `PROTOTYPE-STAND-IN` to find them all, then replace them with the real implementation.

| Class in `org.supermas.prototype` | Owning area | Behaviour our agents rely on |
| --- | --- | --- |
| `InMemoryBlackboard` | Blackboard | One isolated board per task. Agent snapshots are filtered by evidence permissions. Contributions from another scope or unauthorized source are rejected |
| `InMemoryBlackboardFactory` | Blackboard | A new board with a unique task identifier for each email |
| `Capabilities` | Agent configuration | A capability that separates agents able to reply from context agents |
| `InMemoryAgentRegistry` | Agent registry | Registration through configuration only. `replyCandidates()` leaves out context agents |
| `ScriptedCoordinator` | Coordinator | Preprocessing on receive, one action per round, restricted board access for agents, enforced budgets |
| `ScoreRoutingStrategy` | Routing | Clear, ambiguous and absent matches, with candidates and reasons kept |
| `DraftFirstCoordinationStrategy` | Scheduling | The next action is chosen from board state |

## Known gaps for the real implementation

- `Blackboard` cannot record feedback or approval, so revision after rejection does not run.
- Tasks proposed by agents are stored but not scheduled.
- There is no wall-clock timeout around agents.
