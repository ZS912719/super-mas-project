---
id: team-architecture
type: architecture
scope: team-development
updated: 2026-11-05
status: active
---

# Architecture and Contracts

The project targets a blackboard-based multi-agent email reply assistant. The Java baseline defines contracts; the feature implementation described in [Blackboard-Storage-and-Observation](Blackboard-Storage-and-Observation.md) adds storage and observation, not the complete execution pipeline.

## Intended collaboration

`Coordinator` accepts email. `Preprocessor` creates initial analysis and `BlackboardFactory` creates/restores a scoped board. Specialists assess suitability and context sufficiency; routing selects candidates with reasons and a coordination strategy chooses the next action. Agents contribute through restricted views and never invoke peers directly. This is the contract design, not a claim that the workflow currently runs.

| Package under `org.supermas` | Responsibility |
| --- | --- |
| `model` | Scoped email, evidence provenance, tasks, budgets, drafts, feedback and history |
| `blackboard` on main; `blackboard.api` on feature | Snapshot, coordinator control, specialist contribution and preprocessing contracts |
| `agent` | Registry, shared specialist interface and student/teacher/administration/research-project/generic role interfaces |
| `coordinator` | Orchestration, routing and next-action contracts |
| `context` | Thread/domain evidence retrieval and budgeted selection |
| `llm` | Local inference and generation contracts |
| `persistence` | Conversation/configuration repositories; main also contains the original blackboard repository |
| `review` | Version-specific edit, rejection feedback and explicit approval |

## Domain invariants and intended boundaries

- `BoardId` combines project/thread scope with a task identifier. Matching thread identifiers in different projects must not mix evidence.
- `Evidence` distinguishes original facts, retrieved information and inference, with source provenance and permitted audiences.
- `Task`, `ExecutionRecord` and `BudgetUsage` separate work, recorded execution and cumulative resource accounting.
- Draft text, internal explanations, approval, rejection feedback and sent history are distinct records. Editing invalidates prior approval; rejection is not sending.
- `ContextRetriever` retrieves authorized application evidence; `ContextSelector` selects within budget. These contracts do not implement development-vault retrieval.
- `GenerationService.prepare` must account for all input, including current email and feedback. `ModelRequest` separates trusted prompts from untrusted data, but structure alone does not prove injection resistance.
- `ModelResponse` and `GenerationResult` require an unambiguous success/failure outcome. Contract records alone do not establish validation or defensive copying.

Sources: `main:d62e4fc` — `docs/architecture.md`, `src/main/java/org/supermas/model/Domain.java` and the contract packages indexed in [Source-Index](Source-Index.md). Implementation status: [Project-Status](Project-Status.md).
