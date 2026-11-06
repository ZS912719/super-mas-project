# Overview and Architecture

## Problem and goal

A university user receives very different emails (students, colleagues, administration, research partners). The system must propose a reply for each, using only **local** language models so that no mail leaves the machine, and leave the final decision to the user. The statement's hypothesis: *can an organization of specialized agents let reasonably sized local models produce replies adapted to different contexts?* Its conclusion asks for an architecture that differs from "send every email to one model": agents that organize themselves, share information and use different knowledge to solve the problem together.

## The organization (prototype branch, Phase 12)

```
             ┌──────────────── control (BB1): Problem · Strategy · Focus · Policy · To-Do-Set · Chosen-Action ───────────────┐
             │  each cycle: every agent estimates through a read-only view → rate within the focus level → run one agent       │
             └──────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
Email ─▶ MetadataAgent ─▶ BLACKBOARD (SQLite, one per email, every revision kept, Markdown view for Obsidian)
                            level 1  email + analysis
                            level 2  requests ............ RequestSplitter
                            level 3  context ............. discussion, discovered topics, missing-information agents
                            level 4  contributions ....... Student · Teacher · Research · Administration (· Generic fallback)
                            level 5  reply ............... Composer ─▶ GenerationService (template or local LLM) ─▶ draft vN
                          ─▶ user (mailbox): edit · approve · send     reject + feedback ─▶ revision on the board
```

- **Agents are knowledge sources.** Each has a trusted configuration (capabilities, readable sources, prompt), estimates what it could add (`assess`) and acts only through restricted board access (`execute`). Agents never call each other.
- **The board is the only communication medium.** Contributions are private to their author; the coordinator promotes copies to the agents allowed to read them, from trusted configuration and per-document readers.
- **Control is explicit and explained.** A BB1-style controller chooses one action per round and writes the decision (focus, ratings, choice) on the board.
- **Several specialists can answer one email.** An email is split into requests; each specialist that fits contributes for its requests; the composer merges them into one reply.
- **The user decides.** Nothing is sent by the agents; approval is version-specific in the mailbox; rejection with feedback leads to a new version.

## Architecture alternatives considered

| Alternative | Why not chosen | Kept as |
| --- | --- | --- |
| **One model for every email** | The statement asks for the opposite; small models do worse with everything in one prompt | — |
| **Router → one specialist** (statement's minimal architecture, Phase 11) | Mixed emails get a one-sided answer; ambiguity ends in the fallback; the blackboard is only a notepad | Baseline (`--architecture router`), measured against the blackboard |
| **Pipeline with fixed stages** | Cannot react to what is found (missing information, feedback) | — |
| **Contract net** (manager announces, agents bid, manager awards) | Close, but awarding is direct delegation; the blackboard keeps agents anonymous and control inspectable | — |
| **Each specialist drafts, a critic picks** | Duplicate work; still one-sided on mixed emails | Possible extension (critic) |
| **Fully opportunistic control** (rate across all levels) | Tried first: strong specialists acted before context arrived and never used it | Strict successive levels with event triggering |

## Why it complies

- **With the statement:** routing (who fits) is answered per request by estimates, several suitable agents collaborate, the no-fit case goes to the fallback, specialization covers prompt and context, conversation memory is kept and budgeted, the LLM is replaceable by configuration, and nothing is sent without the user. See [compliance](../compliance.md).
- **With the targeted architecture:** levels of abstraction on a shared blackboard, independent condition-action knowledge sources, a scheduler choosing one activation per cycle against a control plan (BB1), KS-specific estimates with generic control in the shell, and the blackboard as community memory (Corkill). The control plan itself is fixed in code, not built by control knowledge sources: the one BB1 feature not implemented.

## Where the code is

| Package | Content |
| --- | --- |
| `model` | Shared records (`Domain.java`) |
| `blackboard.*` | Contracts and the teammate's SQLite board, projection, demo |
| `metadata` | `MetadataAgent` and its stages |
| `context`, `memory` | Retrieval, topics, missing information, history, knowledge loading |
| `agent`, `agent.role`, `agent.collaboration` | Agent contracts, configurable roles, contributions, composer, request splitter |
| `control` | `AgendaStrategy`, control entries |
| `coordinator`, `llm`, `persistence`, `review` | Shared contracts; `llm` also holds the local-model adapter |
| `prototype` | Stand-ins for parts nobody built yet, the coordinator on the SQLite board, `PrototypeApp`, `AgentService` |
