# Stand-Ins

These classes exist only because the Metadata and Context agents need something to run against. Each belongs to a teammate's area. Search for `PROTOTYPE-STAND-IN` to find them all, then replace them with the real implementation.

| Class in `org.supermas.prototype` | Owning area | Behaviour our agents rely on |
| --- | --- | --- |
| `InMemoryBlackboard` | Blackboard | One isolated board per task. Agent snapshots are filtered by evidence permissions. Contributions from another scope or unauthorized source are rejected |
| `InMemoryBlackboardFactory` | Blackboard | A new board with a unique task identifier for each email |
| `Capabilities` | Agent configuration | A capability that separates agents able to reply from context agents |
| `InMemoryAgentRegistry` | Agent registry | Registration through configuration only. `replyCandidates()` leaves out context agents |
