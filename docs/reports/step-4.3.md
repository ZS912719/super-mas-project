# Step 4.3: Discussion Topic Type

## Outcome

`TopicContextAgent` now has the discussion type, which contributes the email's own thread. It also closes the gap from step 3.5 where `retrieveThread` had no caller check, because this path now applies source and message rights.

## What was built

| Item | Role |
| --- | --- |
| `TopicType.DISCUSSION` | Retrieval through `AgentContextAccess.thread` |
| `DiscussionTopicTest` | 9 tests |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| **One agent per project, not one per thread** | Documents list who may read them, so an identifier made per thread could never be permitted. The board supplies the thread, so the agent is stateless | One instance per thread, as the plan's "instantiated when needed" suggests. A stable identifier also removes the plan's risk of thousands of agents |
| Only `RECEIVED` and `SENT` | An unsent draft must not read as something that was said | All kinds |
| Newest first as the rank | Recent messages are usually the most relevant in a thread, and the C1 results support this only indirectly | Oldest first: when the budget is short, it would keep the start and lose the latest state |
| Skip a message that does not fit, and keep looking | A long old message should not cost the room for a shorter one | Stopping at the first that does not fit |
| Remove the incoming email from results | Once indexed, it would otherwise be its own context. Applied to all topic types | Relying on the indexer to add it later |

## Critical points

- **The discussion agent duplicates part of what `retrieveThread` does for others.** The plan names this as a downside of the type. It is included so thread history goes through the same merge and budget as other evidence.
- **The 50-message cap is a judgment.** Longer threads lose their oldest messages. Nothing measured chooses this figure.
- **The direct `retrieveThread(board, query)` still has no rights check.** It is required by the shared `ContextRetriever` interface and has no agent parameter. A caller using it bypasses the checks, so agents must only be given `AgentContextAccess`.
- **Evidence from a subject search can also come from another thread.** Thread isolation for those results depends on the configured sources and not on this step. See `docs/reports/step-3.3.md`.
