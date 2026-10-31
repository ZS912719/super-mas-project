# Step 4.1: Define `assess` for Context Agents

## Outcome

`docs/plans/context-agent-assess.md` now defines what `score`, `sufficientContext` and `reason` mean for a context agent. This is a written definition only. Tests of the behaviour arrive with the agents in steps 4.2, 4.3 and 4.9.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| `sufficientContext` means "running again would add nothing" | It is the one question a scheduler needs answered about a context agent | "Enough context to reply": a topic agent cannot know this, because it does not write the reply |
| Fixed sentences in `reason` | Email text must not travel into logs or into anything later treated as trusted | Including the matched words: helpful for debugging, but it copies untrusted text |
| Divide by `min(3, keywords)` | A long keyword list should not make a topic harder to activate | Dividing by all keywords: longer lists would score lower for the same evidence |
| No index access in `assess` | It may run on many agents for each email. The plan names the cost of every `assess` call as a risk | A trial search inside `assess`: more accurate, but it does the retrieval work twice |

## Critical points

- **Text matching can be steered.** Someone who writes an email can include a topic's keywords and raise its score. That makes an agent run, but it cannot widen what the agent may read. The cost is wasted rounds and possibly irrelevant, though still authorized, evidence.
- **The score has not been calibrated.** The divisor of 3 is a judgment, not a measurement. Only the ranking among subject agents uses it.
- **Teammates have not seen this.** P7 is the project owner's decision, but routing code written by others must not read a context agent's `sufficientContext` with the reply-agent meaning.
