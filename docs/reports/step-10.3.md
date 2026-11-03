# Step 10.3: One Context Agent for Discovered Topics

## Outcome

`DiscoveredTopicsAgent` brings discovered topics into the blackboard loop. It has one fixed identity and fixed rights from trusted configuration, and for each email it searches inside the few current topics that fit best. Five tests pass: an email about rooms gets only room-booking mail, and not the message hidden from the agent; an unrelated email and another project score 0; an email in a clustered thread scores 1; the full loop runs the agent once before the reply, and the merged context is on topic.

## How it works

| Part | Behaviour |
| --- | --- |
| Identity | `AgentConfig` passed in (for example `context-discovered`, capability `context-retrieval`, sources such as the project mail and knowledge). No `.` allowed in the identifier |
| `assess` | Deterministic, no index access. 0 in another project. Per topic: 1 if the email's thread is one of the topic's threads, else keywords matched ÷ min(3, keywords), as for declared subjects (step 4.2). Score = best topic. `sufficientContext` = has run and no need is addressed to it |
| `execute` | The best `maxTopics` topics (ties by identifier). For each: an authorized domain search with the topic's tag as filter and the topic keywords, analysis keywords and new text as query, within `maxCharacters`. With an information need: only the best topic, a supplemental search, and the need's terms first |
| Evidence | Each topic is its own ranked list, labelled `<agent>.<topic>` inside the evidence identifier (`doc#topic:context-discovered.t3`), so the existing merge (RRF, MMR, step 4.5) fuses them without change |
| `ContextFirstCoordinationStrategy` (stand-in) | New constructor with further context agents. Each runs once after the declared topic candidates if its score is above 0. Further retrieval goes to a declared subject agent if one fits, else to the first fitting further agent |

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| **One agent, not one agent per discovered topic** | The retriever and board check rights by agent identifier. A new agent per topic would either have no rights (finds nothing) or need rights granted by discovery, which lets mail decide who reads what. It also costs one round per email instead of one per topic | One `TopicContextAgent` per topic, the original T3 idea |
| A topic list per discovered topic | Keeps the merge's view of overlap: a document found by two topics ranks higher | One merged list from the agent, which hides that signal |
| At most 2 topics per email by default (set by the caller) | Bounds cost; the candidate filter for declared topics does the same with 3 | All fitting topics |
| Strategy runs further agents after the declared ones | Declared topics express a person's intent and keep priority | Running in registry order |

## Critical points

- **This departs from "one agent per topic"** for discovered topics. Declared topics still have one agent each. The plan already asked whether a topic agent is more than a saved query; for discovered topics the answer here is explicitly "a saved query inside one agent".
- **The agent's readers must be configured.** Its identifier must be among the readers of the mail source and of each document, as for every agent. If it is forgotten, the agent finds nothing, which fails closed but quietly.
- **No check against drafting capability inside the agent.** The stand-in board refuses drafts from agents without `draft-reply` (P5), and the configuration should not grant it.
