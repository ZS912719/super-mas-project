# Step 3.2: Exact-Scope Thread Retrieval

## Outcome

`LuceneContextRetriever.retrieveThread` returns only messages of exactly the given project and thread, and only of the requested kinds. `retrieveDomain` follows in step 3.3.

## What was built

| Class | Role |
| --- | --- |
| `context/LuceneContextRetriever` | Implements `ContextRetriever` |
| `ThreadRetrievalTest` | 7 tests |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Exact term match on unanalyzed thread identifiers | `THREAD-1` and `thread-1-extra` are different threads, and tests confirm this | Analyzed text: would fold case and split words |
| Kinds chosen explicitly, and none means nothing | Matches `HistoryQuery`: a draft cannot be read as sent by default | Treating an empty set as "all" |
| The kind appears in the provenance description | A later prompt can tell a draft from a sent message | Hiding it |
| Newest messages win the limit, returned oldest first | Recent history matters most, and time order reads naturally | Oldest first: would lose the latest messages |
| Domain documents have no kind | They can never appear as thread history | A special kind value: not available in the shared enum |
| Empty result gives `sufficient = false` without an error | The plan names cold start as a normal case | Throwing |

## Problems encountered

None.

## Critical points

- **No caller check on this path.** `retrieveThread` has no agent parameter in the shared contract, so it cannot authorize a caller. Only the coordinator should call it. Results keep each document's `permittedAgentIds`, and the board then filters agent reads.
- **The current email may appear in its own history** if it was already indexed, because `HistoryQuery` cannot exclude a message. Callers should filter by identifier or index it after retrieval.
- **`maxMessages` is the only bound here.** `HistoryQuery` has no character budget, so `ContextSelector` must apply one later.
- **Equal timestamps have no fixed order.** Enron dates have one-second resolution, so ties can occur in a real thread.
- **Thread correctness depends on the threader.** Its subject fallback can over-merge (step 1.2), and retrieval would then return another conversation's messages as this thread.
