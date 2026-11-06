# Step 2.2: Registry with Capability Filter

## Outcome

A stand-in registry lets agents be added and removed without coordinator changes, and keeps context agents out of reply candidates. This implements proposal P4 and is marked for team validation.

## What was built

| Class | Role |
| --- | --- |
| `prototype/InMemoryAgentRegistry` | The `AgentRegistry` contract, plus `replyCandidates()` |
| `InMemoryAgentRegistryTest` | 3 tests |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Keep `all()` complete and add `replyCandidates()` | `all()` keeps its contract meaning, and nothing shared changes | Filtering `all()`: the coordinator could no longer find context agents |
| Filter on the `draft-reply` capability | One capability covers both P4 and P5: an agent that cannot draft cannot answer | Two capabilities: no case yet needs them apart |
| Registration order is preserved | Routing candidates become deterministic, so tests and benchmarks repeat | A hash map: order could vary |
| Reject duplicate identifiers and a second fallback | Either would silently change who answers | Last registration wins |

## Problems encountered

None.

## Critical points

- **`replyCandidates()` is not on the shared interface.** The stand-in coordinator must therefore depend on this class, not on `AgentRegistry`. If P4 is accepted, the team should decide whether to add it to the interface.
- **A fallback without `draft-reply` is possible.** Registration does not check this. It would fail when it tried to draft, and the coordinator reports that as a failure.
- **Capabilities are configuration, and not proof.** Whoever writes the configuration decides what an agent can do, so `AgentConfig` must come from a trusted source.
