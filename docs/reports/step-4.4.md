# Step 4.4: Candidate Filtering

## Outcome

`TopicCandidateFilter` picks which topic agents run: the discussion agent and other non-subject agents with a match, plus the best few subject agents. It uses `assess` only and never reads an index.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Non-subject agents outside the subject limit | Their scores do not compare with subject scores, so the discussion agent's constant 1 would otherwise always take a place | One ranking across all types |
| Ties broken by identifier | The same email must select the same agents, or benchmark runs would differ | Registration order |
| An `assess` failure means not running | One faulty agent must not block the others | Failing the whole selection |
| The limit comes from the caller | It is a budget decision, which belongs to whoever owns scheduling | A fixed constant |

## Critical points

- **The limit is not chosen by evidence yet.** Step 4.10 uses 3, and that value is a judgment.
- **The filter sees a coordinator's view in the prototype.** `CoordinationStrategy.next` receives one view, so `assess` is called with it rather than with each agent's own view. Topic agents read only the email and analysis and match their own evidence by identifier, so this leaks nothing today, but a different agent type could read more than it may.
- **Selection is by keyword match only.** A topic with relevant documents but no keyword in the email never runs. This is the cost of the plan's decision to keep `assess` free of index access.
