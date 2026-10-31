# Step 3.4: Index Topic Tags

## Outcome

One document can now carry many topic tags, with search restricted to chosen tags, and a tag filter can never widen permissions. This is the shared-index design (O2) that Phase 4's topic agents will use as filtered views.

## What was built

| Item | Role |
| --- | --- |
| `retrieveDomain(query, topicTags)` | Retrieval limited to documents with at least one of the tags |
| `ProjectIndex.retag` | Replaces a document's tags while keeping its content and rights |
| `TopicTagRetrievalTest` | 5 tests |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| A filter added to the same query | Every permission filter stays in force, and a test confirms it | Separate search path per topic: a second place for a leak |
| An overload rather than a change to `ContextQuery` | `ContextQuery` is a shared record that needs team validation | Adding a field to it now |
| One stored document with many tags | Overlap costs nothing and keeps one evidence identifier | One copy per topic (O1) |
| Exact tag matching | Tags are identifiers, not text. `course` must not match `course-cs204` | Analyzed tags |
| No tags means no topic restriction | Plain domain search keeps working | Treating it as "match nothing" |

## Problems encountered

**The plan's claim that re-tagging needs no re-indexing is only partly true.** Lucene cannot edit a stored document, so `retag` rewrites it from its stored fields. No outside data is needed, but internally it is a re-index of that document.

## Critical points

- **Nothing assigns tags yet.** The tags come from whoever indexes a document. Step 4.2 onwards decides how a tag is chosen, and tests here use hand-written values.
- **The overload is not on the `ContextRetriever` interface.** Phase 4 depends on the concrete class unless the team adds it.
- **Tags must come from trusted configuration or fixed templates.** A tag built from email text could let incoming mail place itself in a topic. The plan already lists this as a trust boundary.
- **Tag queries are not bounded in count.** Filters are not subject to the term cap, but very long tag sets would be slower. Phase 4 should keep them small through candidate filtering.
- **Concurrent re-tagging can lose an update** (step 3.1).
