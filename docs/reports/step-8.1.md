# Step 8.1: Conversation History in the Project Index

## Outcome

`IndexConversationRepository` implements the shared `ConversationRepository` interface. Messages are stored in the same per-project Lucene index that the context agents already search, with their `MessageKind` kept, so the discussion agent finds stored history with no further code. Five tests pass.

## What was built

| Item | Behaviour |
| --- | --- |
| `memory/IndexConversationRepository` | `append` stores a message as an `IndexedDocument` with its kind, detected language, and the readers of the project's conversation source. `find` returns exactly one project and thread, only the asked kinds, the newest `maxMessages` in time order |
| `appendIfAbsent(message, tags)` | Same, with topic tags, and says whether it stored anything. Used by the recorder in step 8.2 |
| `ProjectIndex.find(id)` | Looks up one document by identifier |

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| One store: the project index | Thread retrieval (step 3.2), the topic agents and the merge already read it. A second store would have to be kept in sync | A separate database, which also is a team decision (open decision 7) |
| Readers come from the project's conversation source | Trusted configuration decides who reads mail, as for every other source (P6). A project without one fails closed | Letting the caller pass readers, which would let any caller widen access |
| Identical repeat ignored, conflicting repeat refused | A retried call is harmless. A different record under a used identifier, for example a confirmed send whose identifier an incoming Message-ID already took, must not disappear silently | Overwrite (loses the older record) or ignore all repeats (loses the newer) |
| Commit after each append | A recorded message survives a crash, which matters for `SENT` records. Mail arrives slowly, so the cost does not matter | Committing in batches, which bulk loads still do through `ProjectIndex.addAll` |

## Critical points

- **Readers are frozen at write time.** The retriever intersects with the source's current readers, so removing a reader works on old mail, but adding a reader does not reach old mail without re-indexing. Documented in the class comment.
- **The interface's `append` cannot report a duplicate.** `append` therefore ignores identical repeats silently and throws on conflicts; callers who care use `appendIfAbsent`.
- **No transaction across stores** exists, because there is only one store. If the team later chooses another store for the board, ordering between board and history is theirs.
- The language of a stored message comes from the light English/French heuristic of step 1.4.
