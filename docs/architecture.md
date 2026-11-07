# Blackboard API Design

## Status

This document describes the shared API baseline, whose infrastructure remains unimplemented. [The UML diagrams](uml/README.md) reflect those unchanged contracts. Java 17 remains the baseline. Person 3's independent implementation and JavaFX demonstration are described in [Person 3 usage and integration](person3.md). Deferred guarantees below concern shared contracts; Person 3 implementations enforce their own transport and composition checks.

## Collaboration boundaries

`Coordinator` receives emails and advances tasks. `Preprocessor` produces initial analysis; `BlackboardFactory` creates or restores a blackboard. `BoardId` combines a project/thread `Scope` with a task identifier so multiple tasks can belong to one conversation.

`BlackboardView` exposes a defensive, authorized snapshot. `Blackboard` adds control operations for the coordinator. `AgentBlackboard` permits evidence submission, draft submission and suggested work. It exposes no approval, sending or repository operations. Agents must not invoke peers directly.

`SpecializedAgent.assess` declares suitability and context sufficiency. `RoutingStrategy.select` receives these assessments and retains candidates and reasons. `CoordinationStrategy.next` selects actions from current state rather than traversing every agent.

The five role interfaces are extension points, not instantiated agents. Trusted English prompts, capabilities and source permissions belong to `AgentConfig`. New roles can implement the shared specialist contract directly.

## State vocabulary

| Contract | Meaning |
| --- | --- |
| `Evidence` | Scoped original fact, retrieved evidence or model inference, with provenance and permitted agent identifiers |
| `Task` | Work item with an action, optional selected agent, status and reason |
| `ExecutionRecord` | Execution timing, outcome, scheduling reason and optional failure |
| `BudgetUsage` | Cumulative round, retry and generation counts |
| `Draft` | Versioned outward subject/body, separate internal explanation and review metadata |
| `Feedback` | User feedback tied to one rejected draft version |
| `Approval` | Approval of one task and draft version, without send confirmation |
| `BlackboardSnapshot` | Task state, contributions, agenda, executions, drafts, feedback and routing |

`DraftReviewService.edit` must preserve prior versions and invalidate approval. `reject` saves feedback and exposes revision work to a later `Coordinator.run`. Neither action sends or approves the new output.

`GENERATED_DRAFT`, `USER_EDITED_DRAFT`, `RECEIVED` and `SENT` remain separate history kinds. Future integrations may record `SENT` only after an externally confirmed send. There is no mail transport in this revision.

## Context and generation

`ContextRetriever.retrieveThread` represents initial exact-scope history association. `retrieveDomain` represents authorized domain retrieval and supplemental queries. Authorization must be checked before reading sources and when accepting contributions.

`ContextSelector` selects permitted evidence within a context budget, prioritizing relevant evidence and user-sent content. `GenerationService.prepare` also enforces a total input budget; current email text and feedback are not unlimited exceptions.

`ModelRequest` separates trusted `systemPrompt` from `untrustedData`. Future adapters and role prompts must preserve this boundary. Field separation and prompt instructions alone cannot guarantee resistance to malicious embedded text or disclosure. Output validation and human review remain necessary.

`LocalLlm` abstracts inference. `ModelConfig` identifies a model, endpoint, timeout and output limit. A future simulator and real local adapter can implement the same interface. Endpoint validation, redirect/proxy policy and remote-service prevention are not implemented yet.

`ModelResponse` and `GenerationResult` each require exactly one of success or failure. `FailureKind` distinguishes connection errors, timeouts, invalid output and exhausted budgets. Current records contain no validation logic; implementations must enforce these invariants and defensively copy collections.

## Persistence

- `BlackboardRepository`: task snapshots identified by scoped task identifiers.
- `ConversationRepository`: explicitly typed messages queried by exact scope and kind.
- `AgentConfigRepository`: trusted role definitions independent of coordinator branches.

No storage format has been selected. Exception semantics, atomic updates, duplicate handling, expected-version checks and recovery remain implementation decisions. Repository interfaces must not be interpreted as guarantees of durability or access control until implemented.

## Open decisions

1. Routing scores, confidence thresholds, ties and fallback clarification.
2. Conditions for supplemental retrieval, reclassification and stopping.
3. Agent lifecycle and injection of retrieval and generation services.
4. Configuration format, English role prompts and context authorization rules.
5. Character/token accounting and cumulative execution/revision budgets.
6. Local inference protocol, simulator behavior and output schema validation.
7. Storage format, errors, atomicity, ordering and interrupted-task recovery.
8. Interactive entry point, user identity and explicit approval actions.
9. Identifier validation, collection immutability, state transitions and concurrency.

## Future validation checklist

These are requirements for later behavioral tests, not passing tests in this revision:

- The same sender routes to different specialists in different contexts.
- Ambiguous and unmatched messages have explicit outcomes and retained reasons.
- Insufficient context triggers bounded supplemental retrieval.
- Equal thread identifiers in different projects do not mix data.
- Separate threads remain isolated except for explicitly permitted shared project sources.
- Specialist views and model input contain only authorized evidence.
- Unsent drafts never appear as actually sent facts.
- Feedback creates a new version; user edits invalidate earlier approval.
- Model connection failures, timeouts, invalid outputs and budget exhaustion terminate predictably.
- Embedded instructions cannot grant access or authorize sending.
- Stale versions cannot be approved; no unapproved message can be sent.

Person 3 tests validate transport, independent composition and demonstration UI behavior. The authoritative multi-agent workflow described here remains unimplemented.
