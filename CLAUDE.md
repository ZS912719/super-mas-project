# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

Use English for all project-authored content (documentation, diagrams, identifiers, comments, prompts, UI text, logs, test descriptions, commit messages) and when communicating about this project. This comes from `AGENTS.md`.

## Project status

SuperMAS is a Java API for a blackboard-based multi-agent email reply assistant using local language models. The shared contracts are interfaces and records in `org.supermas.model`, `agent`, `blackboard.api`, `coordinator`, `llm`, `persistence` and `review`. This member's part is implemented: `metadata` (MetadataAgent), `context` (retrieval, topic agents, discovered topics, missing information), `memory` (conversation history and knowledge loading) and `agent.role` (configurable specialized agents and their prompts).

The `prototype` branch (from 2026-11-06) merges this work (`docs/metadata-context-plan`) with the teammate's `codex/blackboard-storage`: the durable SQLite blackboard (`blackboard.impl`, `.model`, `.persistence`, `.export`, `.app`, docs in `docs/blackboard-runtime.md`), formal models in `formal/`, CI in `.github/`, and the Electron/Node desktop mailbox in `mailbox/` (unreviewed by its author's own tag). With `AGENT_SERVICE_URL` set, the mailbox gets drafts from the agents through `prototype.AgentService` (loopback `/v1/reply`, a proposal in `docs/plans/agent-service-api.md`). Since Phase 12 (2026-11-07) the prototype is a real blackboard: `agent.collaboration.RequestSplitter` posts requests, role agents post `Contribution`s for the requests they answer, `agent.role.ComposerAgent` merges them into one draft, and `control.AgendaStrategy` (after Hayes-Roth's BB1, in `src/Bibliography`, ignored by Git) chooses each step from board state and writes `control/v1` entries; `prototype.BoardReviewService` rejects with feedback for revision. Design: `docs/plans/blackboard-control.md`; results: `docs/reports/step-12.md`. On this personal branch the user allowed changing the teammate's code and shared contracts for that (changes listed in the step 12 report). The router path (`ScriptedCoordinator`, `ContextFirst`/`DraftFirst` strategies) remains as the baseline (`--architecture router`). Parts nobody has built yet exist as marked stand-ins in `org.supermas.prototype` (search `PROTOTYPE-STAND-IN`); `LeasedCoordinator` runs the agents on the SQLite board, and `PrototypeApp` runs `.eml` files end to end. There is no email transport from Java; the mailbox does approval and sending. `llm.OpenAiCompatibleLlm` talks to a local engine (Ollama, llama.cpp, LM Studio) with `--llm-endpoint/--llm-model`; the template stand-in is the default. Do not change the mailbox UI without asking (project owner, 2026-11-06). `team-knowledge-vault` holds the team's notes and must never be merged into a code branch; read it with `git show` as `AGENTS.md` describes. `docs/architecture.md` lists the open design decisions; shared ones require the team, so do not settle them silently. `docs/plans/metadata-context-agents.md` is the plan, and `docs/reports/` has one report per step.

## Commands

Requires JDK 17 and Maven 3.9+.

```bash
mvn package    # compile and build target/supermas-1.0.0-SNAPSHOT.jar (library, no main class)
mvn compile    # compile only
```

`mvn test` runs the JUnit 5 suite. Run the prototype with `mvn -q compile exec:java -Dexec.mainClass=org.supermas.prototype.PrototypeApp -Dexec.args="benchmark/data/synthetic-dev"` (options in `docs/reports/step-11.4.md`). The mailbox needs Node.js 22.12+ (`cd mailbox && npm ci && npm test`). Commits use Conventional Commits (`AGENTS.md`). Benchmarks are test-scope main classes run with `mvn exec:java` (see the step reports). No linter is configured. The future behavioral requirements are in the validation checklist in `docs/architecture.md`.

Render UML (needs PowerShell, a JDK, and a PlantUML JAR, by default the Git-ignored `tmp/tools/plantuml.jar`):

```powershell
./docs/uml/render.ps1 [-PlantUmlJar <path>] [-JavaExecutable <path>]
```

`render.ps1` assumes Windows when using `JAVA_HOME` (it appends `bin\java.exe`). On other platforms, pass `-JavaExecutable` explicitly.

## Architecture

All code is under `src/main/java/org/supermas`. Nearly all data types (records and enums) are nested in one file, `model/Domain.java`; the other packages hold interfaces that import `Domain.*`.

Intended flow, which is a contract and not a fixed pipeline (reclassification, supplemental retrieval, revision, and stopping are expressed as `Action` values chosen from current state):

1. `Coordinator.receive` runs `Preprocessor`, then `BlackboardFactory` creates an isolated blackboard for the task.
2. `Coordinator.run` asks `CoordinationStrategy.next` for the next action, under `ExecutionLimits`.
3. `RoutingStrategy` turns each `SpecializedAgent.assess` result into a `RoutingDecision` (clear, ambiguous, or none), retaining candidates and reasons.
4. `ContextRetriever` fetches exact-scope thread history first, then permitted domain evidence. `ContextSelector` and `GenerationService.prepare` choose budgeted model input.
5. A specialist contributes evidence, suggested tasks, and a draft through `AgentBlackboard`.
6. `DraftReviewService` handles edits, rejection with feedback, and approval. Rejection exposes revision work to a later `Coordinator.run`.

### Blackboard access levels

Access control is modeled by the interface a caller receives:

- `BlackboardView`: read-only authorized snapshot.
- `AgentBlackboard`: adds evidence, draft, and task-suggestion submission for specialists. It deliberately has no approval, sending, or repository operations.
- `Blackboard`: coordinator control operations, including `accessFor(AgentConfig)`, which hands out the restricted access. The durable `DefaultBlackboard` refuses it: restricted access comes only from `claim` then `accessFor(TaskLease)`, and execution records only from `complete`.

Agents never call peers directly, and they do not select peers or grant their own permissions. Roles are added by implementing `SpecializedAgent` and registering through `AgentRegistry`, not by adding branches to the coordinator. The five role interfaces (`StudentAgent`, etc.) declare no extra methods.

### Invariants to preserve

The interfaces declare these but do not enforce them, so any implementation must:

- **Scope isolation:** `Scope` is project plus thread, and `BoardId` adds a task identifier. Equal thread IDs in different projects must not mix.
- **Authorization:** check permissions before reading sources and when accepting contributions. Being on the blackboard does not make evidence eligible for model input.
- **Provenance:** original facts, retrieved evidence, model inferences, and drafts stay distinct.
- **Trust boundary:** `ModelRequest` separates the trusted `systemPrompt` from `untrustedData`. Emails, references, history, prior drafts, and feedback are all untrusted and must not override permissions or approval rules.
- **Local-only inference:** model adapters enforce local endpoints and handle connection failure, timeout, and invalid output explicitly.
- **Budgets:** enforce round, retry, generation, total-input, and context limits. Current email text and feedback are not exempt from the input budget.
- **Private contributions:** on the durable board an agent's evidence must have only its author as reader; only the coordinator widens access (`prototype.EvidencePromotion`). An agent's own id must be among its `contextSourceIds` for it to submit inferences.
- **Draft lifecycle:** drafts are versioned and never replaced in place. An edit creates a new unapproved version, and approval covers exactly one version.
- **No implied sending:** approval is not a send. `GENERATED_DRAFT`, `USER_EDITED_DRAFT`, `RECEIVED`, and `SENT` are separate history kinds, and `SENT` is recorded only after an externally confirmed send. Future transport must require version-specific user approval.
- **Exactly-one results:** `ModelResponse` and `GenerationResult` must hold exactly one of success or failure.

## Keeping docs in sync

Java sources are authoritative. When a contract changes, update the matching `.puml` files in `docs/uml`, regenerate the committed SVG and PNG previews together, and update any affected text in `README.md`, `docs/architecture.md`, and `docs/uml/README.md` (which states interface and record counts).

Never edit files until I explicitly approve a plan

## What you must implement

The MetadataAgent: semantic analysis of the email (subject, intent, potential categories, etc.).
The ContextAgent: retrieval of past emails, conversation histories and relevant documents.
The specialized agents (StudentAgent, ResearchAgent, AdministrationAgent, etc.).
Management of the prompts specific to each specialized agent.
Management of the contexts and knowledge specific to each agent.
The memory system: history of exchanges, persistent information and retrieval of relevant context.
The declaration of each agent’s skills so the router can select them.
The production of structured contributions (AgentContribution) intended for the Blackboard.
Management of sources and of the relevance of the retrieved information.
The configuration of the specialized agents, so they can be added or modified without changing the orchestrator.

## What you must NOT implement

The central storage of the Blackboard and its concurrency management.
The global scheduling and synchronization of the agents.
The global state machine for email processing.
The central routing and task-assignment mechanism.
System-wide management of events, timeouts and errors.
The technical integration of the local LLM engine (API, HTTP, model loading).
The final transformation of the contributions into a written email.
The graphical interface and user interactions.
Sending the emails.
The unilateral definition of the shared interfaces, which must be validated by all three members.