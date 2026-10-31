# Step 4.8: Bounded Further Retrieval

## Outcome

When something seems to be missing, the board now gets a recorded inference and one wider search. The whole loop runs from preprocessing to a draft in `ContextPipelineTest`. Further retrieval happens at most once per board, and it can never use the rounds the reply needs: this was tested at every round limit from 2 to 7.

**Whether the extra retrieval finds the missing information is not measured.** The agent does not check this, and no gold data exists.

## What was built

| Item | Role |
| --- | --- |
| `Domain.InformationNeed`, `Task.need` (P2, separate commit) | Carries the need on a task |
| `MissingInformationAgent` | Runs the check, records the inference, suggests one retrieval |
| `TopicContextAgent` | Searches wider when a task carries a need |
| `ContextFirstCoordinationStrategy` (stand-in) | Topic agents, then check, then further retrieval |
| `ScriptedCoordinator` | Now carries out a pending task instead of always creating one |
| `ContextPipelineTest` | 6 tests of the whole loop |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| The bound comes from running the check once | Simple to reason about, and enforced by the check's own evidence entry | A counter on the board: the shared record has no such field |
| The suggestion names no agent | Agents do not choose peers. The coordinator decides, and a test confirms it | The check agent addressing a subject agent, which the board rejects anyway |
| Search terms from a fixed table | Nothing from the email enters the need, as the record's comment states | Email words: no gain, since the query already includes them |
| Keep back 2 rounds for routing and generation | Context is optional and must not fail the reply | No reserve: a long list of topics would end in `BUDGET_EXHAUSTED` |
| Add P2 despite touching `Domain.java` | The plan accepted it, it has a marker and its own commit, and the extra constructor keeps existing callers compiling, which a test run confirmed | A private task object: more code to carry the same data |

## Problems encountered

My first version of two test names claimed more than the tests checked, and the "thread-only" test still matched a subject topic through the email's subject. I renamed them and gave that test its own subject. It now shows the need staying `PENDING`.

## Critical points

- **A need can be left unserved.** With no matching subject topic, no agent can carry it out, so the task stays `PENDING` while the email carries on. Nothing reports this apart from the task's status.
- **Only one agent performs further retrieval:** the first selected non-discussion agent. Another topic might hold the answer. I accepted this to keep the bound simple.
- **A found answer is not recognized.** The agent adds wider results, but nothing checks whether a date actually appears in them, and the inference stays on the board. "Possibly missing" may therefore be stale when a reply agent reads it.
- **`ScriptedCoordinator` matches pending tasks loosely.** An unassigned pending task goes to whichever agent the strategy names next for that action. The strategy prevents misuse today, and a real scheduler should be stricter.
- **A failed agent is not retried.** The coordinator counts a retry for a retryable failure, but `hasRun` then treats that agent as done.
- **Strategies see the full board.** The shared interface passes one view to `next`, so `assess` for candidates receives more than each agent's own view (step 4.4).
