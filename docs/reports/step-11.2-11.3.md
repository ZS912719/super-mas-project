# Steps 11.2 and 11.3: The Agents on the Durable Blackboard

## Outcome

Every agent of this member's part now runs on the teammate's SQLite blackboard **without any change to the teammate's code**, as the project owner decided ("we use the code of the other branch"). The same three end-to-end scenarios that ran on the in-memory stand-in pass on SQLite, plus five durable-only checks. `mvn test`: **298 tests, 0 failures, 0 skipped**. Both benchmarks were re-run: every C3 quality, count and isolation figure on Enron is identical to step 10.4, and the role benchmark output is identical byte for byte.

Commit: `2f4d85a`.

## What the durable board requires, and what our code assumed

Read from `DefaultBlackboard`, `BoardState` and `SqliteBlackboardStore` on `codex/blackboard-storage`:

| Rule of the durable board | What our stand-ins did before | Consequence |
| --- | --- | --- |
| `accessFor(AgentConfig)` throws; an agent writes only through `accessFor(TaskLease)` after `claim` | `ScriptedCoordinator` called `accessFor(config)` | A new coordinator stand-in that leases every task |
| `recordExecution` throws; records come only from `complete` and recovery | The coordinator wrote its own records | Records now come from `complete` |
| `updateTask` only cancels pending tasks | Tasks were set `RUNNING`, then finished, by `updateTask` | `claim` and `complete` instead |
| A contribution must have **exactly its author** as reader "until coordinator promotion" | Context agents gave their findings straight to the reply agents | Agents submit privately; the coordinator promotes |
| A contribution's source must be in the author's `contextSourceIds`; a view shows only evidence from the agent's own sources | Inferences used the author's id as source, which was not in its sources | Each agent lists its own id as a source |
| Between `claim` and `complete`, any other write to the board makes the attempt stale ("Input state changed; restart the task") | — | The coordinator writes nothing during an agent's task. Promotion happens after `complete` |
| `BoardState.transition` rebuilds a claimed task with the six-argument constructor | — | The board's copy of a claimed task **loses its `InformationNeed`** (see critical points) |

## What was built

| Item | Behaviour |
| --- | --- |
| `prototype/LeasedCoordinator` (stand-in) | The `ScriptedCoordinator` loop on `DefaultBlackboard`. Each action is a `PENDING` task the agent claims (lease of 5 minutes in the prototype, `RETRY_SAFE`), works on outside any transaction, and completes. **Each routing assessment is its own leased task**, because only a lease gives an agent its restricted view. A pending suggested task is claimed as it is, and the agent receives the original task with its information need. A history failure is noted with `recordFailure` and the email goes on. Boards are reopened from the store, so `inspect` and `run` work after a restart. `stop` cancels pending tasks, then stops |
| `prototype/EvidencePromotion` (stand-in) | After a **context agent's** task **succeeds**, copies of its new private evidence are added for the agents allowed to read them. Readers = registered agents (not the author) whose trusted configuration lists the evidence's source; for retrieved evidence, narrowed to the document's own readers from the index; for an inference, only if its source is its author. A document the index does not know is not promoted. Copy identifier = original + `@shared` |
| `LuceneContextRetriever.readers(project, document, source)` | A document's readers by the rule retrieval applies: readers stored with the document ∩ the source's current readers. Empty if the document or source is unknown, or they do not belong together |
| `TopicContextAgent`, `DiscoveredTopicsAgent`, `ReplyWork` | Submit evidence with only themselves as reader |
| `MissingInformationAgent` | Its own id is its source; its inference is private. The `visibleTo` constructor parameter is gone: who reads the inference is now trusted configuration, not the agent's choice. `DEFAULT_ID = "missing-information"` |
| `RoleCatalog.config` | Adds the role's own id to its sources, so its reply plans are accepted and visible to it |
| `agents/*.json` (all five roles) | `missing-information` added to `contextSources`: the roles may read the check's inference |
| `ScriptedCoordinator` | Promotes the same way, so in-memory and durable runs behave alike |
| `C3Benchmark` | Promotes after each agent run, as the coordinators do; counts only original entries |
| Tests | `LeasedCoordinatorTest` (8), `EvidencePromotionTest` (4), `DurableEndToEndTest` (the 3 `EndToEndTest` scenarios on SQLite, plus 5); `EndToEndTest` now builds its coordinator in an overridable method |

## Choices and why

| Choice | Why | Alternatives rejected |
| --- | --- | --- |
| **Promotion rule from trusted configuration plus the index**, never from what the agent wrote | The board's rule exists so that an agent cannot widen access. Before, the readers of a finding came from the retriever and were carried by the agent; letting the agent declare them again would undo the rule | Agents keep declaring readers and the coordinator copies them: an agent could widen access |
| **Document readers from the index**, not only source readers | A document's readers are fixed when it is written; a source's readers can grow later. Promoting by source alone would give a reader added later access to older documents, which retrieval itself refuses. Fail closed when the lookup fails | Source readers only: simpler, but wider than retrieval |
| **Only context agents' findings are promoted** | Context agents work for others. A reply agent's own search and plan are for itself; promoting them would fill the board with copies nobody uses | Promote everything: more data, no use |
| **Nothing from a failed task** | A failed task's partial results may be incomplete | Promote partial results: could mislead the reply |
| **`@shared` after the identifier** | The board refuses duplicate identifiers. A suffix after the topic marker leaves `TopicEvidence.documentId` unchanged, so the context merge still folds copies and originals of one document together. With a prefix, one document would count as two in the prompt | Prefix `shared:`: breaks the merge; a new id scheme: changes every reader |
| **Author not among the copy's readers** | It already reads its original; seeing both would duplicate it | — |
| **The role's own id added in code, the check's id in JSON** | An agent must always accept its own plans, so this cannot be forgotten in a file. Reading the check is a permission and belongs in trusted configuration, visible per role | Both in JSON: forgetting the own id breaks plan submission |
| **One leased task per assessment** | Their board gives a restricted view only through a lease. Building a filtered view in the coordinator would copy their filtering code and could drift from it | A coordinator-made view: duplicated access logic |
| **History failures as `recordFailure`**, not as a task | The board has no place for a coordinator note, and no `Action` fits history storage. The failure is retryable and the status is left alone, so a draft can still await review | A fake task with a misleading action |
| **`RETRY_SAFE` for every lease** | No task here has an outside effect: retrieval and template drafting only write to the board. Generation with a real model will need `REQUIRE_CONFIRMATION` if it ever has side effects | — |

## How it was verified

| Check | Result |
| --- | --- |
| `EndToEndTest` scenarios (student request, no fitting role, injection) on SQLite | Same outcomes as in memory |
| Restart: a new store on the same file reads the same board | `assertEquals(state, restored)` |
| Every task terminal, one execution record per terminal task, 5 leased assessments | Pass |
| Every submission private; every copy keeps provenance and text; the staff guide never reaches the student | Pass |
| The check's inference reaches exactly the 5 roles, no context agent | Pass |
| Markdown projection audits as faithful | Pass |
| A contribution readable by others is refused by the board and fails the task | Pass |
| Nothing promoted from a failed task; an agent that throws becomes a recorded failure | Pass |
| A suggested task reaches its agent with its information need | Pass |
| **C3 benchmark** (Enron, 7 mailboxes, 10,206 messages, 914 + 405 queries) against `docs/benchmark/results/c3-discovered-enron.md` | Every quality, count and isolation figure identical. Discovery time 9,559 → 8,899 ms (noise); merge latency p50 0.3 → 0.4 ms, p95 4.2 → 4.8 ms (promotion does one index lookup per document) |
| **Role benchmark** (24 synthetic emails) against `docs/benchmark/results/roles-synthetic.md` | Identical byte for byte |

The C3 benchmark was run with `C3Benchmark benchmark/data/enron-raw/maildir 10000 200 <report> <index>`. These arguments reproduce the query counts of step 10.4, which did not record its command.

## Problems encountered

1. **Three tests failed after the agents went private.** Each was the new rule working: a role's sources now include its own id (`RoleCatalogTest`); a test reply agent had to list the check as a source to read it (`ContextPipelineTest`); retrieved evidence is promoted only with the index lookup (`DiscoveredTopicsAgentTest`). The tests were updated to state the new rule. No assertion was loosened.
2. **A first durable test expected a missing-information inference for a student email** that lacks nothing. Diagnosed from the board's execution records ("Nothing missing"), not a promotion fault. The test now uses an email that triggers the check.
3. **The C3 benchmark would have silently measured nothing.** Its reader saw no evidence once agents submitted privately, so every C3 variant would have scored zero while the run still "succeeded". Found by reading every caller of the changed agents before committing; fixed by promoting in the benchmark too.

## Critical points

- **Defect in the teammate's code, worked around and not fixed:** `BoardState.transition` (`blackboard/model/BoardState.java`) drops a task's `InformationNeed` when it is claimed or completed, because it uses the old constructor. The agent still gets the need (the coordinator hands over the original task), but **the board's history loses it**. The one-line fix (pass `task.need()`) is for the teammate to make, or to approve.
- **The durable board does not check the drafting capability** (our proposal P5). Any agent holding a lease can submit a draft. The coordinator only gives `GENERATE` to reply candidates, but a context agent could write a draft during its own task. To be raised with the teammate.
- **Their input validation is strict:** any write by anyone during an agent's task makes it stale. With one sequential coordinator this never happens. Parallel agents on one board would need their per-resource dependency tracking, which they list as deferred.
- **Leases are not renewed.** No heartbeat is sent; a task longer than the 5-minute lease would be fenced. Fine for templates; a local model may need heartbeats.
- **Promotion is the coordinator's job in their design.** These stand-ins are a proposal for how the real coordinator does it, not a decision. It touches shared rules (who reads what) and needs the team.
- **Copies double the evidence on a board** (originals plus copies). Harmless at this size; the Markdown projection shows both.
