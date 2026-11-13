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

The window loads bundled UI without Node access. Authorization opens in the system browser, using expiring single-use state and PKCE to return to the initiating session. Development Web credentials remain local. The Windows release path below embeds public desktop client IDs.

## Behavior and boundaries

Demo sending is simulated. Generation requires a local model. Save draft → Approve → Send uses version-specific approval; edits invalidate approval. Provider acceptance means submission, not confirmed delivery. Session messages, tokens, labels and drafts are in memory and cleared on exit.

Classification is an import adapter, not autonomous agent execution. Local generation calls Ollama directly, not Java `GenerationService`. The mailbox does not implement coordinator, specialist or blackboard integration.

## Remaining review

Recorded tests simulate provider/model responses. Live OAuth and inference remain separate validation work. Public distribution requires review of native authorization/client-secret handling, signing and deployment. Durable storage, attachments, rich HTML and background synchronization remain deferred.

Build and historical evidence: [Build-CI-and-Delivery](Build-CI-and-Delivery.md), [Validation-and-Limits](Validation-and-Limits.md).

## Desktop drafting and review feature branch

See [Mailbox-Draft-Review](Mailbox-Draft-Review.md) for the opt-in Java drafting flow and mailbox versioned review on the 2026-11-13 feature branch.

## Windows native OAuth release preparation (2026-11-13)

Published code commit `8eea014fbe2b9ec41c01969e7b687ebc47d35e7e` on `codex/mailbox-draft-review` adds Windows release packaging with public Gmail and Microsoft desktop client IDs. The existing loopback browser handoff uses PKCE and single-use state. The local session supports read-only consent and concurrent refresh coalescing. Outlook submits a threaded reply through Graph Mail.Send after approval. Development builds can use the existing Web clients. See `mailbox/desktop/main.js`, `mailbox/scripts/release-config.cjs`, `mailbox/lib/providers.js`, `mailbox/server.js`, `.github/workflows/release.yml` and `mailbox/README.md`.

The live Java drafting path is opt-in and uses `/v1/private-reply`. It isolates each conversation in a fresh in-memory SQLite blackboard, Lucene index and topic catalog, and rejects remote inference. The demo bridge keeps its existing endpoint. Sources include `mailbox/lib/agents.js`, `src/main/java/org/supermas/prototype/AgentService.java` and `docs/plans/agent-service-api.md`.

The 16 mailbox tests, 45 targeted Java tests and frontend build passed. Electron Builder produced an unsigned Windows portable build with fixture IDs. Inspection confirmed that both IDs were embedded and no environment file was packaged. Real OAuth, mailbox access, live model inference, and hosted release CI remain unverified. The remote feature branch was verified at `926c18972e49761d1a489773111015689e403e48`. The project must supply both registrations before building a tester release. Any rollback of this code commit supersedes these additions.
