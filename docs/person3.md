# Person 3: usage and integration

## Run

Use JDK 17 and Maven 3.9+. From the repository root: `mvn test`, `mvn package`, then `mvn javafx:run`. Tests need a graphical desktop for JavaFX; no model is needed. The default application uses fixed, clearly labeled demonstration data and simulated responses.

For real inference, start an already installed Ollama instance with cloud features disabled (`OLLAMA_NO_CLOUD=1`, then restart Ollama). Choose a model already present locally. Set the endpoint, model and timeout in the UI, enable the local Ollama checkbox, and compose a selected sample. Settings are session-only; no model is downloaded. See [Ollama local-only configuration](https://docs.ollama.com/faq).

## Small implementation boundaries

- `OllamaLlm` implements unchanged `LocalLlm` using Java HTTP and Gson. `/api/chat` is non-streaming. System instructions and untrusted data use separate messages. Input/output character limits, a 4 MiB HTTP-body limit, timeout and interruption are enforced. No retries, global scheduler or automatic downloads exist. Reuse the client and call it off the UI thread.
- `CompositionService` implements unchanged `GenerationService`. Nested `Input`/`Contribution` are local preparation types, not an accepted shared `AgentContribution` API. `prepare(Input)` blocks missing essentials and explicit conflicts before inference; `generate` returns existing `GenerationResult`.
- Output JSON must contain exactly `subject`, `body`, `language`, `usedEvidenceIds`, and `commitments`. References are checked structurally against supplied IDs and retained in internal explanation metadata. Explicit internal markers such as `[[evidence:1]]` are rejected in outward text; ordinary words/numbers matching raw IDs are allowed. Declared commitments must be allowed; common undeclared English/French promises are conservatively rejected. These checks cannot prove factual grounding, actual language/tone or detect every unsupported promise. Human review remains necessary.
- The existing Blackboard-based `prepare` overload consumes the supplied `ContextSelection`, checking scope/access and budgets without retrieval or relevance selection. It preserves `ORIGINAL_FACT`, `RETRIEVED_EVIDENCE` and `MODEL_INFERENCE` categories, without treating retrieval as verification. It defaults to English/professional tone. This limited adapter requires the caller to establish completeness, conflict status and outward-disclosure permission; shared contracts cannot express them. Evidence without producer metadata remains unattributed. Blank incoming subjects are supported.
- `MailApplication` uses one background worker and JavaFX tasks. Selection/cancellation invalidates pending results; a result cannot overwrite edits made while generation ran. A small map restores unsaved subject/body buffers and original generation metadata by workspace entry ID. After editing, the evidence explanation is labeled as belonging to the original generation only. Buffers last only for the application session and are not authoritative storage. `DraftEditGuard` holds only a UI edit revision, not task state.
- `WorkspaceProvider` is a UI-local read boundary for future adapters to P1/P2 public APIs. `ui.demo.DemoWorkspace` provides three fixed examples and deterministic model responses, without agent execution or persistence.
- Approval is disabled pending an authoritative service. Demo rejection only displays local feedback; it does not persist rejection or schedule revision. No sending capability exists. Agent configuration awaits P2's API.

## Local execution boundary

Only HTTP base URLs with explicit ports and hosts `localhost`, `127.0.0.1` or `[::1]` are accepted. `localhost` is pinned to `127.0.0.1`. User information, non-root paths, queries and fragments are rejected. HTTP redirects and proxy routing are disabled. No cloud fallback or raw-prompt logging exists.

A loopback URL does not prove an arbitrary server never forwards data externally. Controlled server configuration and egress verification remain deployment responsibilities. Cancelling an HTTP request does not guarantee immediate server-side inference termination; P1 must reject late results at publication. Character budgets are not silently converted into token limits.

Oversized HTTP responses produce non-retryable `INVALID_OUTPUT`, distinct from connection/timeouts. Tests cover draft navigation and metadata, delayed/cancelled results, short evidence IDs, the transport limit, a second loopback redirect receiver, interruption after server receipt, and UI selection of the actual HTTP adapter against a controlled server. The UI-to-HTTP test includes HTTP and malformed-response failures without simulated fallback. No real model quality or server-side egress behavior is established by these tests.

## Shared decisions still pending

1. **P1/P2/P3:** contribution schema, provenance, essential-information and conflict signals, outward-disclosure permissions. Local fixtures are not replacements for P2 agents or shared storage.
2. **P1/P3:** composer invocation, input revision/execution identity and atomic publication rejecting cancelled/stale results. No `ComposerAgent` orchestration adapter or lifecycle change is supplied.
3. **P1/P3:** task listing/observation and version-specific edit/reject/approve APIs. No authoritative review state is implemented in the UI.
4. **P2/P3:** validated configuration query/update API and when agent changes take effect.
5. **Team:** additions to generation options and shared DTOs, if needed. Existing shared Java contracts remain unchanged.

Replacing demo entries requires an adapter implementing the small local `WorkspaceProvider` read interface. Real review needs additional application-facing commands once agreed; the view's disabled actions must then delegate to the authoritative service, not mutate local approval flags.
