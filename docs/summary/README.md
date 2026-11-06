# Project Summary

A short, structured entry point to everything that was decided, built and measured. Each part summarizes its architecture, the choice made, the alternatives considered, and why it complies with the targeted architecture (a blackboard system after Hayes-Roth's BB1 and Corkill) and with the project statement. Details live in the step reports in `docs/reports/`; each page links to them.

| Part | Page |
| --- | --- |
| The whole system: goal, organization, flow, architecture alternatives | [overview-and-architecture.md](overview-and-architecture.md) |
| Email analysis (`MetadataAgent`) | [metadata.md](metadata.md) |
| Context, retrieval, topics, missing information, memory | [context-and-memory.md](context-and-memory.md) |
| Specialized agents, prompts, contributions, composer | [specialized-agents.md](specialized-agents.md) |
| Blackboard storage (teammate) and blackboard control (Phase 12) | [blackboard-and-control.md](blackboard-and-control.md) |
| Generation and the local LLM | [llm-and-generation.md](llm-and-generation.md) |
| Desktop mailbox (teammate) and the agent service | [mailbox-and-integration.md](mailbox-and-integration.md) |
| Benchmarks, tests and results | [evaluation.md](evaluation.md) |

Cross-cutting documents: [compliance check](../compliance.md), [to-do for a fully compliant project](../todo.md), [code analysis](../code-analysis.md), [design note of the blackboard control](../plans/blackboard-control.md), [plan with every phase](../plans/metadata-context-agents.md), [list of stand-ins](../plans/stand-ins.md).

## Timeline

| Date | What happened | Where |
| --- | --- | --- |
| 2026-10-05 → 10-24 | Team contracts: Java interfaces and records, UML, English-only rule | `main`, `docs/architecture.md` |
| 10-31 | This member's plan; Phase 0 (benchmark, data schema), Phase 1 (metadata: parsing, threading, stripping, language, entities, speech acts) | Reports 0.x, 1.x, `sessions/autonomous-session-2026-10-31.md` |
| 10-31 → 11-03 | Phase 2 (stand-ins), 3 (Lucene retrieval, C1 baseline), 4 (topic agents C3, missing information) | Reports 2.x-4.x |
| 11-03 | Phase 8 (memory), 10 (discovered topics), 9 (specialized agents, prompts, plans) | `sessions/autonomous-session-2026-11-03.md` |
| 11-02 → 11-05 | Teammate: CI, desktop mailbox, durable SQLite blackboard, Markdown projection, formal models, team knowledge vault | Teammate's branches |
| 11-06 | Phase 11: branch `prototype` merges both; agents run on the SQLite board; runnable prototype; mailbox asks the agents | Reports 11.x, `sessions/prototype-session-2026-11-06.md` |
| 11-07 | Phase 12: real blackboard (several specialists, composer, BB1 controller, revision); local-LLM adapter; compliance, summaries, code analysis, cleanup | `step-12.md`, `sessions/night-session-2026-11-07.md` |

## Who did what

This member (Thomas): metadata, context and retrieval, memory, topics, specialized agents and prompts, the prototype wiring, Phase 12 control, the agent service. The teammate (shengxun zhao): the Java contracts with the team, CI, durable blackboard storage and observation, formal models, desktop mailbox, team knowledge vault. Code from either side changed by the other is listed in the step 11 and 12 reports.
