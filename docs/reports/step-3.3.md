# Step 3.3: Authorized Domain Retrieval

## Outcome

`retrieveDomain` searches only sources the requesting agent may use, and checks that before any index is read. The confused-deputy case is tested: a broad agent fetching for a specialist gets only what the specialist may see. This is project-owner decision P6.

## What was built

`LuceneContextRetriever.retrieveDomain`, and `DomainRetrievalTest` with 11 tests.

## The authorization rule

A source is usable only if all of these hold:

1. It is known, from trusted configuration.
2. It belongs to the board's project.
3. Its `permittedAgentIds` include the requester (`ContextQuery.agentId`).
4. The requester's `AgentConfig.contextSourceIds` include it.

Inside an allowed source, the document's own `permittedAgentIds` must also include the requester. The rights on returned evidence are document rights ∩ source rights.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Authorize before the index is read | The project's invariant, and a bug in search cannot reveal denied sources | Filtering results afterwards |
| Require both source and agent configuration to agree | Either side alone can remove access, which is the safer direction | Trusting one of them |
| Denied sources are named in `explanation`, not thrown | Part of a query can still work, and the denial is visible. Identifiers came from the caller, so nothing new leaks | Failing the whole query |
| Evidence rights exclude agents the source does not allow | A document's list cannot widen a source | Copying the document's list |
| Evidence carries the board's scope, with the original thread in provenance | The board rejects any other scope, and the origin stays traceable | Changing the board's rule |
| Over-budget results are left out whole and counted | A cut document can change meaning, and the count keeps it visible | Truncating |
| A later smaller result may still fit | Budget is not wasted because one large document is ranked high | Stopping at the first that does not fit |
| Supplemental means 30 results rather than 10 | A simple, bounded way to search deeper | Query expansion: a design choice needing evaluation |

## Problems encountered

None.

## Critical points

- **`ContextQuery.agentId` is not authenticated.** The retriever trusts it, so a caller could name a more privileged agent. Agents must receive a retriever bound to their identity, or the coordinator must build the query. Phase 4 must not hand agents a free-form version.
- **Past mail from other threads needs care.** Thread isolation holds only if `conversation` sources are not granted to agents for domain search. Granting one is an explicit cross-thread permission, and `docs/architecture.md` only allows that with explicit permission.
- **Evidence scope hides origin.** A result from another thread carries the board's scope, so isolation checks on `Evidence.scope` do not detect it. Only the provenance text shows the origin, and that is a plain string.
- **Supplemental retrieval returns earlier results again,** because the retriever is stateless. The merge in step 4.5 removes duplicates by identifier.
- **The limits of 10 and 30 are placeholders.** Nothing was measured.
- **Sufficiency means "at least one result".** It says nothing about whether results are relevant. Step 4.1 defines it properly for context agents, under P7.
- **The explanation can mention other sources' identifiers,** which are visible only to whoever asked for them.
