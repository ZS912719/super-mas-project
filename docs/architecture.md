# Blackboard API Design

## Status

The Java module groups contracts and implementations by responsibility. Blackboard code lives under `org.supermas.blackboard`: `api` defines service contracts, `impl` implements the blackboard, `model` holds shared state types, `persistence` defines storage contracts, `persistence.sqlite` implements SQLite, `persistence.codec` handles serialization, `export` generates Markdown, and `app` wires demo/observer entry points. Java 17 remains the baseline. SQLite is authoritative; Markdown is a generated observation view opened in Obsidian. Task execution is protected by short pessimistic transactions and durable fenced leases. See [runtime design and usage](blackboard-runtime.md), [formal models](../formal/README.md) and [UML](uml/README.md).

The separate [mailbox application](../mailbox/README.md) runs in an Electron desktop window. Its main process owns a Node.js loopback HTTP server serving the bundled UI. Provider authorization opens in the system browser and returns to the initiating desktop session through expiring single-use OAuth state and PKCE. It implements mail transport, session state, draft review and direct local reply generation without implementing `Coordinator`, specialist agents, blackboard storage or `GenerationService`. Its classification adapter accepts externally supplied JSON; automatic agent execution remains deferred. These application modules are intentionally separate from the Java contract UML until an integration protocol is agreed.

On the `prototype` branch, the metadata, memory, context and specialized agents run on this SQLite blackboard through `prototype.LeasedCoordinator`, a marked stand-in for the coordinator. Every agent action is a leased task. Agent contributions stay private to their author, as the blackboard requires, and `prototype.EvidencePromotion` copies a context agent's findings to the agents whose trusted configuration lists the source, narrowed to each document's readers in the index. Both are proposals for the real coordinator, not decisions (open decision 10). Since Phase 12 the prototype is a blackboard in the sense of Hayes-Roth's BB1: requests, context, contributions and the reply are levels on the board, several specialists contribute to one email, a composer merges them, and `control.AgendaStrategy` chooses each step from board state and records it (`docs/plans/blackboard-control.md`, `docs/reports/step-12.md`). For this, the prototype branch added `Blackboard.viewFor`, `Blackboard.recordFeedback` and `Action.CONTRIBUTE` to the shared contracts. `prototype.PrototypeApp` runs the whole flow from `.eml` files; see [step 11.4](reports/step-11.4.md).

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

`GENERATED_DRAFT`, `USER_EDITED_DRAFT`, `RECEIVED` and `SENT` remain separate history kinds in the Java contract. The mailbox keeps drafts separate from provider history, marks provider acceptance as submitted, and populates real Sent history only through provider synchronization.

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

`blackboard.persistence.BlackboardRepository` defines basic snapshot persistence; `BlackboardStore` extends it with versioned atomic commands and task/export coordination. `blackboard.persistence.sqlite.SqliteBlackboardStore` implements this contract with insert-only legacy save, immutable historical JSON snapshots, transactional export requests, task leases and idempotent completion receipts. `blackboard.impl.DefaultBlackboardFactory` creates or opens coordinator handles. `DefaultBlackboard` implements the coordinator interface and creates restricted, lease-bound specialist views. Both depend on `BlackboardStore`, not the SQLite class. The runtime requires `accessFor(TaskLease)`; the original `accessFor(AgentConfig)` and direct execution-record insertion fail to prevent unfenced mutations. Conversation/configuration repositories remain under the general `persistence` package; human review remains a separate contract.

`blackboard.export.MarkdownProjector` depends on `BlackboardStore` to read committed revisions and acknowledge publication, without importing the SQLite adapter or blackboard implementation. It generates complete version directories and atomically publishes their entry. A vault-level publisher lock coordinates file publication; it is separate from SQLite task locks. Read-back audit checks generated contents against historical database state. Entry points in `blackboard.app` select the concrete SQLite adapter; the observer handles expired leases and retries export. Obsidian and manually edited knowledge never write authoritative state. See [failure windows and current limits](blackboard-runtime.md#projection-and-recovery).

## Open decisions

1. Routing scores, confidence thresholds, ties and fallback clarification.
2. Conditions for supplemental retrieval, reclassification and stopping.
3. Agent lifecycle and injection of retrieval and generation services.
4. Configuration format, English role prompts and context authorization rules.
5. Character/token accounting and cumulative execution/revision budgets.
6. Local inference protocol, simulator behavior and output schema validation.
7. Storage migrations/retention, dependency-specific conflict detection and external-side-effect recovery.
8. Interactive entry point, user identity and explicit approval actions.
9. Broader domain validation and context permissions beyond the initial runtime boundaries.
10. The coordinator's promotion rule: who may read a copy of an agent's private contribution. The prototype proposes trusted configuration plus document readers ([step 11.2-11.3](reports/step-11.2-11.3.md)).

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

The Maven build now verifies blackboard storage, concurrency and recovery behavior, and on the `prototype` branch an end-to-end run of the agents on that storage with stand-ins (`DurableEndToEndTest`). It does not validate the complete proposed multi-agent workflow: revision after rejection, approval and sending are not connected. [Blackboard validation](blackboard-validation.md) records implemented coverage. The mailbox has separate behavior tests and a runnable demo.
