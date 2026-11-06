# Step 2.3: Scripted Coordinator Loop with a Round Limit

## Outcome

A stand-in coordinator takes an email through preprocessing, routing, a specialist and presentation for review, one action at a time and within budget. In its tests it runs the real `MetadataAgent.rulesOnly()` with fake specialists.

## What was built

| Class | Role |
| --- | --- |
| `prototype/ScriptedCoordinator` | The loop, budget checks, retries and failure recording |
| `prototype/ScoreRoutingStrategy` | Clear, ambiguous or no match, from scores, keeping candidates and reasons |
| `prototype/DraftFirstCoordinationStrategy` | Chooses route, generate or present from board state |
| `ScriptedCoordinatorTest` | 12 tests |

## Behaviour covered by tests

- A full loop ends in `AWAITING_REVIEW` with one unapproved draft, 2 rounds and 1 generation attempt.
- Ambiguous and absent matches go to the fallback agent, and all candidates stay on record.
- Context agents are never assessed as reply candidates.
- A strategy that never ends stops at the round limit with `BUDGET_EXHAUSTED`.
- Retryable failures retry within the retry limit, and the generation limit stops further attempts.
- An agent breaking a board rule becomes a recorded `AGENT` failure, and its draft is not stored.
- Stopped boards do no more work, and unknown boards are rejected.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Strategy chooses every action from state | The contract says this is not a fixed pipeline, and Phase 4 can add retrieval without changing the coordinator | A hard-coded sequence |
| Each non-terminal action costs one round (P8) | Simple and bounded, and the assumption is marked in code | Exempting context agents: a volunteer loop could run unbounded |
| `STOP` and `PRESENT_DRAFT` cost nothing | A loop that has just spent its last round can still finish | Charging them: a complete draft could fail on the limit |
| Budget checked before work is done | No action runs beyond the limit | Checking afterwards: one extra model call per run |
| Agents assess through restricted access | Assessment cannot read evidence the agent is barred from | Passing the coordinator's board |
| A retryable failure stays out of `recordFailure` until final | The board's `failure` means the task failed. History stays in the execution records | Recording each one: a successful retry would still look failed |
| Depends on `InMemoryAgentRegistry` rather than the interface | `replyCandidates()` exists only there (step 2.2) | Filtering inside the coordinator: moves the assumption further from the registry |
| Fixed action → agent resolution: named agent, else fallback | The strategy already reads routing, so the coordinator has no routing logic of its own | Reading routing in two places |

## Problems encountered

1. One test name, `budgetsAccumulateAcrossRuns`, claimed more than it proved. It only shows that a finished board does no further work, so I renamed it. See the first critical point.

## Critical points

- **Cumulative budgets across runs are not exercised.** Usage is stored on the board and never reset, but no path makes one board process in two separate `run` calls, because any finished status ends the loop.
- **Revision after rejection does not work yet.** The contract says rejection exposes revision work to a later `run`. Here, `AWAITING_REVIEW` is final, and `Blackboard` has no way to record feedback (step 2.1). This needs the team's review design.
- **Tasks proposed by agents are stored but never scheduled.** The strategy does not read the pending agenda yet. Phase 4 must handle this if context agents rely on proposals, or those tasks are silently ignored.
- **The loop has no wall-clock timeout.** A hanging agent blocks `run` forever, because round limits only count completed actions. Timeouts belong to the team's global error handling.
- **Agent exception messages are copied to the board.** They could contain untrusted email text, so nothing should treat them as instructions.
- **`maxInputCharacters` and `maxContextCharacters` are not enforced here.** They belong to generation and context selection. Step 3 onwards must apply the context limit.
- **`stop` during a running `run` is only partly safe.** The loop notices at its next step, but a later status write from the active step could overwrite it. The stand-in is intended for single-threaded use.
- **The routing thresholds (0.5 and 0.1) are placeholders.** Nothing was measured.
