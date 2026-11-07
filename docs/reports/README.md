# Reports

One report per step of the plan ([`docs/plans/metadata-context-agents.md`](../plans/metadata-context-agents.md)): what was built, the choices and why, problems, critical points. Session protocols (unattended work, merges) are in [`sessions/`](sessions/). For a shorter overview by project part, start with [`docs/summary/`](../summary/README.md).

| Phase | Topic | Reports |
| --- | --- | --- |
| 0 | Foundations: assumed decisions, tests, schema, benchmark runner, synthetic data | `step-0.1` … `step-0.6`, `labeling-setup`, `data-import-enron` |
| 1 | `MetadataAgent`: parsing, threading, stripping, language, models, entities, speech acts, gate | `step-1.1` … `step-1.10`, `step-1.3b` |
| 2 | Stand-ins: in-memory board, registry, coordinator, isolation | `step-2.1` … `step-2.4` |
| 3 | Retrieval: index, thread history, authorization, topic tags, C1 baseline | `step-3.1` … `step-3.5` |
| 4 | Topic agents (C3), selection, missing information, correspondent topics, C3 vs C1 | `step-4.1` … `step-4.10` |
| 8 | Memory: history in the index, recorder, restart, knowledge loading | `step-8.0` … `step-8.5`, `fix-drafts-in-search` |
| 9 | Specialized agents: assess, roles as configuration, plans, prompts, generation stand-in, benchmark | `step-9.1` … `step-9.8` |
| 10 | Discovered topics: discovery, regrouping, agent, benchmark | `step-10.1` … `step-10.4` |
| 11 | Prototype branch: merge, durable board, promotion, runnable prototype, mailbox connection | `step-11.0-11.1`, `step-11.2-11.3`, `step-11.4`, `step-11.6` |
| 12 | A real blackboard: several specialists, composer, BB1 controller, revision, benchmark, local-LLM adapter, review fixes | `step-12` |
| 13 | Online model (OpenRouter) for development, local for delivery; record and replay | `step-13` |

Phases 5-7 (optional LLM checker, correction iteration, finish) were not run; their content moved into Phases 11-12 and [`docs/todo.md`](../todo.md).

| Session protocol | Date |
| --- | --- |
| [`sessions/autonomous-session-2026-10-31.md`](sessions/autonomous-session-2026-10-31.md) | Phases 1 (part) |
| [`sessions/autonomous-session-2026-11-03.md`](sessions/autonomous-session-2026-11-03.md) | Phases 8-10 |
| [`sessions/prototype-session-2026-11-06.md`](sessions/prototype-session-2026-11-06.md) | Phase 11 |
| [`sessions/night-session-2026-11-07.md`](sessions/night-session-2026-11-07.md) | Phase 12 and the night's tasks |
