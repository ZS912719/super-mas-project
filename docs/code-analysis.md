# Code Analysis: Bugs, Risks, SOLID and Design Patterns

**Scope:** the Java code on the `prototype` branch on 2026-11-07 (about 90 main classes), with the mailbox where it meets Java. **Method:** reading the code, with the step reports, the benchmark trails and the control entries as evidence; each defect claimed was reproduced by a test before it was fixed. **Approved rule for the night:** fix only clear, low-risk bugs, each with a test; put larger refactorings in [`todo.md`](todo.md).

## 1. Defects fixed tonight (each with a test that fails without the fix)

| # | Defect | Where | Fix | Test |
| --- | --- | --- | --- | --- |
| D1 | A further-retrieval need was claimed by the discussion agent, which ignores needs: the wider search never happened | `control/AgendaStrategy`, `context/TopicContextAgent` | A source that already ran is asked about the pending task it would take; the discussion agent rates a need at 0 | `BlackboardCollaborationTest.aFurtherRetrievalNeedIsNotTakenByTheThreadAgentWhichCannotServeIt` |
| D2 | Without a generation service, the composer re-composed every round until the budget ran out | `agent/role/ComposerAgent.assess` | Rates 0 once the current contributions are composed | `…withoutAGenerationServiceTheComposerComposesOnceAndStops` |
| D3 | A budget failure left a control decision in the controller's per-board map (a slow leak) | `prototype/LeasedCoordinator.step` | Decision taken right after `next` | `…aBudgetFailureLeavesNoControlDecisionBehind` |

Found and fixed earlier tonight while building Phase 12 (see `docs/reports/step-12.md`): promoted copies parsed as version 0; context agents re-running after every contribution. Found in step 11: drafts leaking into domain search (fixed then).

## 2. Remaining risks, by severity

| Severity | Finding | Where | Why it matters | Suggested action (to-do) |
| --- | --- | --- | --- | --- |
| **High** | **Cross-role disclosure through the composer.** Excerpts from role-restricted sources (e.g. the staff-only teaching guide) reach the composer's prompt whenever that role contributes, and could be quoted in a reply to a student | `ReplyWork.contribute` excerpts, `ComposerAgent.execute` | Statement §5.1 "information it must never disclose"; confidentiality between contexts | Tag sources with an audience (staff-only, student-visible…) and let the composer receive only excerpts whose audience includes the email's sender. Mitigation added tonight: `prompts/composer.txt` now tells the composer never to quote or reveal internal documents. A prompt rule is not an access control; with a real model this must be tested |
| High | **No real model measured.** Output validation, timeouts and prompt budgets are only tested against a fake engine | `llm/*` | The statement's central constraint | Run the benchmarks with a chosen local model |
| Medium | **Request extraction misses phrasing such as "I would also like…"** (en-m07: 1 request found, gold 2) | `RuleRequestExtractor.ASKING` | Missed requests are never contributed to | Allow adverbs inside the patterns; measure on both synthetic sets before and after |
| Medium | **One threshold dominates collaboration** (0.4 vs 0.2: multi-request exact 5 vs 10 of 12) | `AgendaStrategy.Settings` | Specialists with one or two keywords never join | Choose on a fresh validation set; consider per-request estimates |
| Medium | **Broad `catch (RuntimeException)` in 22 places** (coordinators, agents, service) | Mostly `prototype/*`, agents | Robustness, but programming errors become board failures and can hide bugs | Catch narrower exceptions where possible; keep the broad catch only at agent boundaries, always recording the message |
| Medium | **The Java board cannot approve or edit**; the mailbox approves, so the Java history never gets `SENT` or `USER_EDITED_DRAFT` from real use | `BoardReviewService.edit/approve`, `DefaultBlackboard.setStatus` | Statement §9 "responses actually sent" | A review endpoint in the agent service, and approval on the board |
| Low | **Unbounded request queue** in the agent service (single thread, unbounded executor queue) | `AgentService` | A local token holder can queue work without limit | Bounded queue, 503 when full |
| Low | **Control history parsed on every cycle** (`ControlEntry.lastChoice` reads every entry for every agent) | `control/ControlEntry` | Quadratic in long revisions | Keep a per-board index of the last choice, or store the round on the agent's last task |
| Low | **French is routed by keywords only** | Role JSON, metadata | 7 of 9 single-intent mismatches are French | Deferred by plan decision |

## 3. SOLID

| Principle | Assessment | Evidence |
| --- | --- | --- |
| **Single responsibility** | Good in the domain packages (one class per stage, store, agent kind). Weak in the entry points | `PrototypeApp` (339 lines: CLI parsing, wiring, printing), `AgentService` (380: HTTP, validation, JSON mapping, history) |
| **Open/closed** | Good for agents (JSON roles), prompts, sources, strategies, generation. Weak where capability names decide behaviour | `AgendaStrategy.level` maps capability strings to levels; the literal `"draft-reply"` appears in `AgendaStrategy`, `BoardState`, `Capabilities` and the demo |
| **Liskov substitution** | **Violated by the shared `Blackboard` contract**: the durable board throws for `accessFor(AgentConfig)`, `recordExecution`, and refuses `setStatus(APPROVED)`; `GenerationService.prepare` throws in both implementations | `DefaultBlackboard:50,129`, `TemplateGenerationService:40`, `LlmGenerationService:34` |
| **Interface segregation** | `Blackboard` mixes coordinator control, legacy access and status; `GenerationService` mixes preparation and generation though agents only generate | `blackboard/api/Blackboard.java`, `llm/GenerationService.java` |
| **Dependency inversion** | Good at agent level (agents depend on `AgentBlackboard`, `ContextRetriever`, `GenerationService`). Coordinators depend on concrete classes | `LeasedCoordinator` and `ScriptedCoordinator` take `InMemoryAgentRegistry` (for `replyCandidates()`, missing from `AgentRegistry`) and `DefaultBlackboardFactory`; `AgendaStrategy` checks `instanceof Blackboard` and imports capability constants from `agent.role` |

Other principles from statement §12: encapsulation is good (records, defensive copies, package-private collaborators), cohesion high, coupling low through the board. Duplication to remove: "Re: subject" and generation-result validation in `ReplyWork` and `ComposerAgent`; SHA-256 helpers in six classes; promotion logic in both coordinators; the two coordinators share most of their loop.

## 4. Design patterns: in use, and where one would help

In use and justified: Strategy, Factory, Repository, Observer, Mediator (the board), Adapter, Facade, Command (see [compliance §4](compliance.md#4-design-patterns-in-use-statement-12)).

| Where | Pattern that would help | Benefit |
| --- | --- | --- |
| Draft generation in `ReplyWork.run` and `ComposerAgent.execute` (prompt → generate → validate → subject → submit) | **Template Method** or a small `DraftWriter` collaborator | One validation path; the invariant "exactly one of draft or failure" checked in one place |
| `PrototypeApp.Options` (four telescoping constructors) | **Builder** | Readable configuration as options grow |
| Capability strings deciding levels and permissions | A **registry of capabilities** (value object + lookup) | Open/closed: a new level or permission without editing the controller or the board |
| Optional `ControlRecording` checked with `instanceof` in the coordinator | **Observer** (a `ControlDecisionListener` injected into the strategy) or returning a richer decision object | No type check; strategies that explain themselves need no special casing |
| Board status changes spread over `setStatus` callers | **State** | Legal transitions (processing → awaiting review → processing after rejection → approved) enforced in one place |
| Promotion rules (`EvidencePromotion`) | **Specification** | Combinable, testable reader rules (config, document readers, audience) — useful for the disclosure fix |
| The two coordinators | Extract the common loop (**Template Method**) | One budget and failure policy |

Not recommended: Chain of Responsibility (the controller's ratings already choose among sources), Singleton (no global state is needed).

## 5. Strengths worth keeping

- Trust boundary is structural: trusted prompt from configuration, untrusted data in labelled blocks, never mixed; contributions cannot carry instructions to the composer.
- Isolation is tested (0 violations in thousands of probes), and authorization happens before any index is read.
- Every decision is explained on the board (control entries, `selectionReason`, plans with evidence references).
- The teammate's storage is careful (fenced leases, atomic projection, formal models); the prototype now uses it without bypassing its rules.
- Tests: 328 Java, 14 mailbox, plus formal models; each defect fixed has a regression test.
