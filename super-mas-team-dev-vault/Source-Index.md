---
id: team-source-index
type: index
scope: team-development
updated: 2026-11-07
status: active
---

# Source Index

Paths are repository-relative. The baseline column is mandatory context: a path on a feature branch may not exist in main. Inspect with `git show <ref>:<path>` or browse a checkout of that branch. Full source remains in Git; this vault stores navigation and explanatory summaries.

| Topic / keywords | Inspected ref | Entry points |
| --- | --- | --- |
| Domain, scope, email, evidence, budget, draft | `main` | `src/main/java/org/supermas/model/Domain.java` |
| Blackboard contracts, preprocessing, views | `main` | `src/main/java/org/supermas/blackboard/Blackboard.java`, `src/main/java/org/supermas/blackboard/AgentBlackboard.java`, `src/main/java/org/supermas/blackboard/BlackboardFactory.java`, `src/main/java/org/supermas/blackboard/BlackboardView.java`, `src/main/java/org/supermas/blackboard/Preprocessor.java` |
| Agent roles and suitability | `main` | `src/main/java/org/supermas/agent/SpecializedAgent.java`, `src/main/java/org/supermas/agent/AgentRegistry.java` and role interfaces in that directory |
| Coordinator, routing, next action | `main` | `src/main/java/org/supermas/coordinator/Coordinator.java`, `src/main/java/org/supermas/coordinator/RoutingStrategy.java`, `src/main/java/org/supermas/coordinator/CoordinationStrategy.java` |
| Retrieval and budgeted selection | `main` | `src/main/java/org/supermas/context/ContextRetriever.java`, `src/main/java/org/supermas/context/ContextSelector.java` |
| Generation, local inference | `main` | `src/main/java/org/supermas/llm/GenerationService.java`, `src/main/java/org/supermas/llm/LocalLlm.java` |
| Versioned review and approval | `main` | `src/main/java/org/supermas/review/DraftReviewService.java` |
| Conversation/configuration storage | `main` | `src/main/java/org/supermas/persistence/ConversationRepository.java`, `src/main/java/org/supermas/persistence/AgentConfigRepository.java`, `src/main/java/org/supermas/persistence/BlackboardRepository.java` |
| Contract architecture and UML | `main` | `docs/architecture.md`, `docs/uml/README.md`, `docs/uml/class-diagram.puml`, `docs/uml/package-overview.puml`, `docs/uml/data-contracts.puml`, `docs/uml/service-contracts.puml`, `docs/uml/render.ps1` |
| CI, module detection, draft release | `main` | `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `docs/ci-cd.md` |
| Distribution, launcher, nuXmv install | `main` | `scripts/package-blackboard.ps1`, `scripts/start-blackboard.ps1`, `scripts/install-nuxmv.ps1`, `docs/blackboard-distribution.md` |
| Java dependency pins | `main` / feature | `pom.xml` (different dependencies on each branch) |
| SQLite transactions and recovery | `codex/blackboard-storage` | `src/main/java/org/supermas/blackboard/persistence/sqlite/SqliteBlackboardStore.java`, `src/main/java/org/supermas/blackboard/persistence/BlackboardStore.java` |
| Enqueue, claim, contribute, complete | `codex/blackboard-storage` | `src/main/java/org/supermas/blackboard/impl/DefaultBlackboard.java`, `src/main/java/org/supermas/blackboard/impl/DefaultBlackboardFactory.java` |
| Lease, fencing, board state | `codex/blackboard-storage` | `src/main/java/org/supermas/blackboard/model/BlackboardData.java`, `src/main/java/org/supermas/blackboard/model/BoardState.java` |
| JSON and digests | `codex/blackboard-storage` | `src/main/java/org/supermas/blackboard/persistence/codec/JsonCodec.java` |
| Markdown publication and audit | `codex/blackboard-storage` | `src/main/java/org/supermas/blackboard/export/MarkdownProjector.java` |
| Demo, observer and runtime invocation | `codex/blackboard-storage` | `src/main/java/org/supermas/blackboard/app/BlackboardDemo.java`, `src/main/java/org/supermas/blackboard/app/BlackboardObserver.java`, `scripts/run-blackboard.ps1`, `docs/blackboard-runtime.md` |
| Blackboard behavior and projection tests | `codex/blackboard-storage` | `src/test/java/org/supermas/blackboard/impl/BlackboardTest.java`, `src/test/java/org/supermas/blackboard/export/MarkdownProjectorTest.java`, `docs/blackboard-validation.md` |
| Kripke, nuXmv, CTL/LTL, counterexamples | `codex/blackboard-storage` | `formal/tasks.smv`, `formal/projection.smv`, `formal/README.md`, `scripts/check-formal.ps1` |
| Runtime UML | `codex/blackboard-storage` | `docs/uml/runtime.puml` |
| Electron, OAuth, providers, local model | `feat/mailbox-desktop` | `mailbox/desktop/main.js`, `mailbox/desktop/navigation.js`, `mailbox/server.js`, `mailbox/lib/providers.js`, `mailbox/lib/model.js` |
| Draft review, classification, UI | `feat/mailbox-desktop` | `mailbox/lib/drafts.js`, `mailbox/lib/classification.js`, `mailbox/web/app.js` |
| Mailbox setup, dependencies and tests | `feat/mailbox-desktop` | `mailbox/README.md`, `mailbox/package.json`, `mailbox/package-lock.json`, `mailbox/test/mailbox.test.js`, `mailbox/VALIDATION.md` |
| Shared development entry point | Current working tree | `AGENTS.md`, `super-mas-team-dev-vault/Home.md` |

## Search from the repository root

```powershell
rg -n -i 'lease|fencing|recovery' super-mas-team-dev-vault
git show codex/blackboard-storage:docs/blackboard-runtime.md
```

Do not switch branches just to read a source file. Branch tips and symbols may evolve; use the snapshot mapping in [Project-Status](Project-Status.md) when reproducing historical claims.

## Generation workspace on prototype

See [Generation-UI-Integration](Generation-UI-Integration.md) for the exact merge revision. Entry points: `composition/CompositionService.java`, `llm/OllamaLlm.java`, `ui/MailApplication.java`, `ui/WorkspaceProvider.java`, `ui/DraftEditGuard.java`, `ui/demo/DemoWorkspace.java` under `src/main/java/org/supermas/`; usage: `docs/person3.md`; build: `pom.xml`. The existing prototype pipeline is separate.
