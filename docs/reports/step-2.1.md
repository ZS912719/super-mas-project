# Step 2.1: In-Memory Blackboard with Permission Checks

## Outcome

A stand-in blackboard now enforces scope, source permissions, provenance, draft versioning and draft rights. It is prototype code, marked for replacement by the team's blackboard. `origin/main` was checked first and contains no such work.

## What was built

| Class | Role |
| --- | --- |
| `prototype/InMemoryBlackboard` | One task's board, with restricted access for each agent |
| `prototype/InMemoryBlackboardFactory` | Creates boards with a unique task identifier, and restores snapshots |
| `prototype/Capabilities` | The `draft-reply` capability name |
| `InMemoryBlackboardTest`, `Fixtures` | 11 tests |

## Rules enforced

| Rule | Behaviour |
| --- | --- |
| Reading | An agent's snapshot contains only evidence naming it in `permittedAgentIds`. The coordinator sees everything |
| Scope | Evidence or tasks from another scope or board are rejected |
| Evidence source | `RETRIEVED_EVIDENCE` needs a source in the agent's `contextSourceIds` |
| Provenance | Agents cannot submit `ORIGINAL_FACT`, and `MODEL_INFERENCE` must name the agent itself as its source |
| Batches | One bad item rejects the whole batch |
| Drafts | Only agents with `draft-reply`, only in their own name. Each gets a new version, always unapproved |
| Tasks | Proposals become `PENDING`, and cannot target a peer |
| Copies | Inputs and snapshots are copied or unmodifiable |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Agent identity comes from `accessFor`, not from input | An agent cannot claim to be another agent | Trusting `proposal.agentId()` alone |
| Filter evidence in the snapshot itself | "Being on the board" cannot leak to an agent by accident | Asking each agent to filter |
| `SecurityException` for permission failures | Separates a rule violation from malformed input, and the coordinator can record it as an agent failure | A return value that callers could ignore |
| All-or-nothing batches | No half-applied contributions | Storing the valid part: agents could not tell what was accepted |
| Older `PROPOSED` drafts become `SUPERSEDED` | The enum exists for this, and text stays untouched | Leaving several proposals open at once: approval target becomes unclear |
| One lock | Enough for a prototype. Concurrency belongs to the team | Finer-grained locking |
| Hide nothing but evidence from agents | The contract only specifies evidence permissions | Filtering tasks and executions too: no rule to base it on |

**P5 is implemented here**, marked with `ASSUMED-TEAM-DECISION`.

## Problems encountered

None during implementation. All 11 tests passed on the first run, which also means no test has yet been shown to fail for the right reason, apart from what the assertions state directly.

## Critical points

- **It is not the real blackboard.** Nothing is persisted, and the design does not suggest how the team should store boards.
- **A contract gap.** `Blackboard` has no operation for recording feedback, approval or a reviewed draft. Those lists can only arrive through `restore`. `DraftReviewService` presumably fills this role, and the team must decide how.
- **Agents can widen sharing.** An agent sets `permittedAgentIds` on its own evidence, and the board cannot check that against source rights, because `AgentConfig` holds only the submitting agent's sources. Proposal P6, in the retriever, addresses this at retrieval time. The real board should also handle it.
- **Tasks, executions, routing candidates and drafts are visible to every agent.** Reasons or summaries could expose information from evidence an agent may not read. Implementers should avoid putting evidence into those fields, and the team should decide whether to filter them.
- **`setStatus` is unrestricted**, including `APPROVED`. Only coordinator code holds `Blackboard`, but the real board should validate transitions.
- **`restore` trusts its snapshot.** It must receive a full coordinator snapshot, because a filtered agent snapshot would silently lose evidence.
- **Email text is untrusted** and is stored as it arrives. The board does not interpret it.
