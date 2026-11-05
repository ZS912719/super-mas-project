---
id: team-mailbox
type: implementation-summary
scope: team-development
updated: 2026-11-05
status: feature-branch
source_ref: c039b37
---

# Independent Desktop Mailbox

Available on `feat/mailbox-desktop`, also included in the inspected blackboard branch. Not tracked in the inspected `main`. Sources: feature-branch `mailbox/README.md`, `mailbox/package.json`, `mailbox/VALIDATION.md` and `docs/architecture.md`.

## Components

| Source | Role |
| --- | --- |
| `mailbox/desktop/main.js` | Own local server lifecycle and isolated Electron window |
| `mailbox/desktop/navigation.js` | Navigation and provider-authorization boundaries |
| `mailbox/server.js` | Loopback HTTP API and session handling |
| `mailbox/lib/providers.js` | Gmail/Microsoft provider and OAuth adapters |
| `mailbox/lib/model.js` | Direct local Ollama requests |
| `mailbox/lib/drafts.js` | Draft versions, approval and submission guards |
| `mailbox/lib/classification.js` | Externally supplied classification JSON |
| `mailbox/web/` | UI and animation integration |
| `mailbox/test/mailbox.test.js` | Separate mailbox tests |

The window loads bundled UI without Node access. Authorization opens in the system browser, using expiring single-use state and PKCE to return to the initiating session. Provider credentials are user-supplied configuration, never shared project knowledge.

## Behavior and boundaries

Demo sending is simulated. Generation requires a local model. Save draft → Approve → Send uses version-specific approval; edits invalidate approval. Provider acceptance means submission, not confirmed delivery. Session messages, tokens, labels and drafts are in memory and cleared on exit.

Classification is an import adapter, not autonomous agent execution. Local generation calls Ollama directly, not Java `GenerationService`. The mailbox does not implement coordinator, specialist or blackboard integration.

## Remaining review

Recorded tests simulate provider/model responses. Live OAuth and inference remain separate validation work. Public distribution requires review of native authorization/client-secret handling, signing and deployment. Durable storage, attachments, rich HTML and background synchronization remain deferred.

Build and historical evidence: [[Build-CI-and-Delivery]], [[Validation-and-Limits]].
