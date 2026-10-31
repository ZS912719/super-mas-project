# Step 4.2: Subject Topic Agent

## Outcome

`TopicContextAgent` works for declared subjects, with a deterministic `assess` and no LLM. It also closes two gaps left by Phase 3: an agent's identity is now fixed outside its own control, and something now assigns topic tags.

## What was built

| Item | Role |
| --- | --- |
| `TopicDefinition`, `TopicType` | A topic as trusted configuration |
| `TopicContextAgent` | One class for all topic types, built from a definition with a fixed template |
| `AgentContextAccess` | Retrieval tied to one agent. No method takes an agent identifier |
| `TopicEvidence` | Evidence identifiers of the form document plus agent |
| `TopicTagger` | Assigns subject tags at indexing time |
| `LuceneContextRetriever.retrieveThread(board, query, agentId)` | Thread history with source rights checked. Used from step 4.3 |
| `TopicContextAgentTest`, `TopicTestSupport` | 12 tests and their shared setup |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| `AgentContextAccess` with no agent parameter | Closes the risk from step 3.5 where `ContextQuery.agentId` could name a more privileged agent | Checking the caller's identity: Java has no reliable way to know the caller |
| One evidence entry per topic per document | The board stores an identifier once, so a second topic's find would otherwise be lost, and step 4.5 needs every topic and rank | A single entry: loses the ranks that fusion needs. A new shared record: needs team validation |
| Encode the two parts in the identifier | Needs no shared contract change, and `TopicEvidence` is the only code that reads it | Parsing the provenance text: more fragile |
| Current text, not the body, for matching | Quoted text is someone else's earlier words, and a test confirms it is ignored | The full body |
| Topic words first in the query | The term limit is 256, and a long email must not push out trusted words | Email text first |
| An `UncheckedIOException` becomes a retryable `CONTEXT` failure | A read failure is a recoverable condition that the coordinator should decide about | Letting it escape |

## Problems encountered

A document found by a topic that has already reported it was first going to fail the board's duplicate check. `submit` now removes those before submitting, using identifiers of the form document plus agent.

## Critical points

- **Keyword tagging can be steered.** A message containing a topic word gets that topic's tag at indexing time. It can enter a topic, but the tag does not change who may read it. The effect is noise in a topic's results.
- **Tagging only happens if the indexer calls it.** Nothing wires `TopicTagger` to an ingest path yet, because there is no ingest path in this project. The benchmark in step 4.10 is its first real caller.
- **Evidence entries multiply.** Three topics finding one document leave three entries on the board. Anything other than the merge step must not count them as three pieces of evidence, and the plan names this risk. The prototype's `snapshot` shows them all.
- **`AgentContextAccess` protects against mistakes, not hostile code.** Agents run in one JVM, and code with a reference to the retriever could still call it directly. Isolation against malicious agents would need separate processes.
- **`ranAlready` needs evidence.** An agent that ran and found nothing reports `sufficientContext` false, so a scheduler relying only on that field would run it again. The stand-in strategy in step 4.8 avoids this by checking its executed tasks, but a teammate's scheduler must also do so.
