# Step 3.1: Per-Project Lucene Index

## Outcome

Each project now has its own Lucene index, with English and French stemming. It is on disk or in memory, and it supports exact thread lookup, filtered search and re-tagging. Steps 3.2 to 3.4 expose these through the retriever.

## What was built

| Class | Role |
| --- | --- |
| `context/IndexedDocument` | One searchable item: message or domain document, with permissions and topic tags |
| `context/ProjectIndex` | One project's index: `add`, `addAll`, `thread`, `search`, `retag`, `commit` |
| `context/ProjectIndexes` | Gives exactly one index per project |
| `ProjectIndexTest` | 9 tests |

Lucene 9.12.3 was added (`lucene-core`, `lucene-analysis-common`).

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Lucene 9.12 | Plan's choice, and the project targets Java 17 | Lucene 10: needs Java 21 |
| One index per project **and** a project filter in each query | Physical separation, plus a second barrier if indexes are ever mixed up | Trusting either one alone |
| No query parser, terms built from analyzed text | Email text cannot inject syntax such as `*:*` | `QueryParser` with escaping: one missed case becomes a leak |
| One body field per language, and queries search both | Stems stay correct for each language | One field and one analyzer: French stemming would break, or the reverse |
| Hash for the folder name | A project identifier cannot escape the root folder, and no characters need escaping | Using the identifier as a path: path traversal |
| Query terms capped at 256 per field | A huge email cannot exceed Lucene's clause limit | Failing on long mail |
| Refuse documents from another project on `add` | Stops isolation errors at the point of entry | Relying on query filters only |
| Add replaces by identifier | Re-indexing cannot create duplicates | Append-only |
| Store all fields in the index | Results need no second store, and re-tagging needs no outside data | A separate document store |

## Problems encountered

None in this step. The class already contains the operations steps 3.2 to 3.4 need, so a test or fix for them may touch it later.

## Critical points

- **Index files contain full email text** without encryption. On-disk indexes of personal mail must stay in an ignored, access-controlled folder.
- **The 256-term cap silently drops later terms.** Which ones are dropped depends on order of appearance in the query, so a very long query loses its end.
- **Language comes from the caller.** A wrong language value stems a document with the wrong analyzer and reduces recall.
- **Cross-language search is not supported.** A French query matches English documents only through shared word forms. The plan already flags this for embeddings.
- **`add` refreshes the searcher every time,** which is slow for many documents. `addAll` is for bulk loads.
- **`commit` is explicit.** Anything not committed is lost if the process crashes.
- **Single-process use only.** Lucene's write lock prevents two processes opening one folder for writing, and that open fails.
- **Concurrent `retag` and `add` on one document could lose an update,** because retagging reads and then writes.
