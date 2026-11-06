# What `assess` Means for a Context Agent

`SpecializedAgent.assess` was written with reply agents in mind. A context agent never answers the email, so its assessment needs its own definition. This is decision P7 in `docs/plans/assumed-decisions.md`.

## The fields

| Field | Reply agent | Context agent |
| --- | --- | --- |
| `score` | How suitable the agent is to answer | How strongly this email matches the agent's topic, from 0 to 1. It says nothing about the agent's ability to reply |
| `sufficientContext` | The agent has enough to answer | **Running this agent again would add nothing:** it already has evidence on this board, and no information need is addressed to it |
| `reason` | Free explanation | A fixed sentence with counts. It never contains email text |

## Rules

1. **Deterministic, with no LLM.** The same email and the same board give the same assessment.
2. **Read-only and cheap.** `assess` does not touch an index or any other source, and it may be called on many agents for one email.
3. **A match grants nothing.** Email text can raise a score, because the text is what is being matched. A score decides whether the agent runs. It never decides what the agent may read, which stays with the retriever's permission checks.
4. **Another project scores 0.** An agent configured for one project never volunteers on a board from another.
5. **No match is normal.** A new sender or a new topic gives score 0 and `sufficientContext` false, and no failure. The same applies to a retrieval that finds nothing.
6. **Not a reply candidate.** Context agents lack the `draft-reply` capability, so routing never considers them (P4), and they cannot submit drafts (P5).

## How the score is computed

| Type | Score |
| --- | --- |
| Subject | Distinct topic keywords found in the email's subject and new text, divided by `min(3, number of keywords)`, capped at 1. Three matches are enough for full confidence, however long the list is |
| Discussion | 1 when the agent's project is the board's project, because every email has a thread |
| Correspondent | 1 when the switch is on and the sender is verified, otherwise 0 |

Keywords match whole words, ignoring case, and a keyword of several words must appear as a phrase.

## What readers must not assume

- **`sufficientContext` true does not mean the evidence is good**, only that more of the same retrieval is pointless.
- **`sufficientContext` false does not mean context is missing for the reply.** That is the job of the missing-information check.
- **Scores from different types are not comparable as quality.** A discussion agent always scores 1. Candidate filtering therefore selects by type first and ranks only subject agents by score.
