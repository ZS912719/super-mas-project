---
id: team-generation-ui-integration
type: integration
scope: team-development
updated: 2026-11-07
status: published-code
---

# Generation Workspace Integration

## Source and delivery

On 2026-11-07, remote prototype was verified after publishing workflow commit `8b26ce3`. The independent generation branch was fetched at `4563c2afff30d32dcf6d53b1537f29e9a105c922`. Its earlier contract baseline is `b82834e`.

The merge on prototype is `5504b71e925bd25765ef7462e974e4b040aad0c3`. The merge was pushed to origin/prototype and the remote tip was verified at this exact commit on 2026-11-07. It preserves the existing multi-agent pipeline and Electron mailbox while adding the generation workspace. The source feature branch is unchanged. No merge to main is claimed.

## Resulting behavior

- Prototype already runs metadata, memory, Lucene context retrieval, missing-information checks, specialist contributions and composition on SQLite. The mailbox can opt into Java AgentService. Earlier integration-deferred statements apply to their historical branch snapshots, not this prototype.
- `composition.CompositionService` supplies its own preparation input, readiness/conflict checks and JSON output validation. It uses `blackboard.api.BlackboardView` after adaptation. It is not wired into ComposerAgent.
- `llm.OllamaLlm` provides native loopback `/api/chat`, separate instruction/data messages, a whole-exchange timeout, interruption and response-size limits. Existing OpenAI-compatible generation stays unchanged.
- `ui.MailApplication`, `WorkspaceProvider`, `DraftEditGuard` and `ui.demo.DemoWorkspace` form an independent JavaFX sample. Delayed results cannot overwrite edits or a newly selected email. No live board, authoritative approval, rejection persistence or sending is added to this UI.
- Maven preserves prototype dependencies and JUnit 5.12.2, adding Gson 2.13.2, JavaFX Controls 17.0.18 and javafx-maven-plugin 0.0.8. Linux CI uses Xvfb; Windows uses its graphical desktop.

## Validation on 2026-11-07

- `mvn -B -ntp clean verify`: 407 tests, 0 failures, 1 error, 4 skipped. The error is the pre-existing KnowledgeLoaderTest symbolic-link test: Windows reports that the client lacks the required privilege. Optional GLiNER and speech-act tests skip without model files. This is not a fully green build.
- The three added test classes account for 38 tests, with 0 failures/errors. They use controlled model/HTTP responses, not a real model.
- `npm test` in mailbox: 13 passed, one failed because loopback port 14318 was already occupied. No existing process was stopped and mailbox code was not changed.
- `mvn -B -ntp -DskipTests package` passed, confirming compilation and packaging separately from the failing full test run. Staged whitespace checks passed. Hosted Linux/Windows CI and a real Ollama engine were not revalidated.

## Remaining integration

Agree one composition input format and explicitly adapt the existing prompt builder before wiring CompositionService. Choose whether JavaFX is an additional demonstration or a future product interface; the Electron mailbox remains available. Source audience permissions, semantic factual grounding, authoritative version-aware review and send confirmations remain separate work. UI edit revisions are not blackboard fencing tokens.

Sources at the code revision above: `pom.xml`, `.github/workflows/ci.yml`, `README.md`, `docs/person3.md`, `docs/architecture.md`, `src/main/java/org/supermas/composition/`, `src/main/java/org/supermas/llm/OllamaLlm.java`, `src/main/java/org/supermas/ui/` and their three test classes. Existing prototype sources: `src/main/java/org/supermas/prototype/PrototypeApp.java`, `src/main/java/org/supermas/prototype/AgentService.java`, `src/main/java/org/supermas/agent/role/ComposerAgent.java`, `mailbox/lib/agents.js`.

Related: [Project-Status](Project-Status.md), [Source-Index](Source-Index.md), [Validation-and-Limits](Validation-and-Limits.md).
