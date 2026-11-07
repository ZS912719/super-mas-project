# Step 13: An Online Model for Development, Local for Delivery

**Date:** 2026-11-07. **Branch:** `prototype`. **Approved by the project owner** in the session of 2026-11-07: OpenRouter as the online provider, the mailbox only with a second opt-in.

## 1. Why

This laptop (Intel i5-8265U, 4 cores, 7.1 GB RAM with about 2.3 GB free, integrated Intel UHD 620) can run only 1.5-3 B models on its processor, at an estimated tens of seconds to minutes per reply. The project owner chose to develop against an online model and switch to a local one on a stronger computer before delivery. The plan's Phase 5 rule already allowed this as a temporary, explicit, off-by-default development exception; this step builds it.

The owner asked whether the model matters much, since it only words the reply from the board's data. The answer recorded here: the facts, requests and plans come from the blackboard, so routing, retrieval, composition and access control are model-independent; but rule following (no invented facts, no internal documents, asking for missing data, language), covering every request of a long prompt, resistance to injected instructions, and latency all depend strongly on the model, and small local models are much weaker. **The experiments of statement §15 must be measured again on the delivered local model.**

## 2. What was built

| Part | What |
| --- | --- |
| `llm.LlmSettings` | One record for the engine: provider `template` (default), `local`, `online`, `replay`; model; endpoint; API key; cache folder; mailbox opt-in. Read from `SUPERMAS_LLM_*` variables, overridden by `--llm-*` flags. The key and the mailbox opt-in exist only as variables. Validates everything at start; `describe()` and `toString()` never show the key |
| `llm.OpenAiCompatibleLlm` | Two modes. The local adapter (unchanged rules: literal loopback HTTP origin, no credential ever sent). The online adapter `online(key)`: HTTPS API base only (no credentials, query or fragment in the URL), key as a bearer token, `.../chat/completions` under the base. Neither follows redirects. Error statuses now carry the engine's own message (shortened to 200 characters); 429 and 5xx are retryable |
| `llm.RecordedLlm` | Decorator that records each successful reply (`llm-recording/v1` JSON under a SHA-256 of model, trusted prompt and untrusted data; atomic write), and a replay engine that answers only exact matches. A miss is an explicit failure |
| `llm.LlmGenerationService` | Names the engine in the draft's rationale ("Online model (development stand-in) ...", "Recorded model ...") |
| `PrototypeApp` | `Options` now holds `LlmSettings`; `Wiring.withGeneration` builds the engine from it; prints `Model: ...` first. `main` reads the environment; `Options.parse(args)` ignores it so tests never call a real model by accident |
| `AgentService` | Reads the same settings. `checkModel` refuses `online` without `SUPERMAS_LLM_ONLINE_MAIL=yes`, and refuses recording or replay (a recording would keep replies to real mail on disk). Prints the model at start |
| Docs | Setup guide [`docs/plans/llm-setup.md`](../plans/llm-setup.md) (online now, local later, SSH tunnel, replay, context window) |

## 3. Choices

| Choice | Alternatives | Why |
| --- | --- | --- |
| **Same adapter, two modes** | A separate online class; a provider SDK | OpenRouter speaks the same chat API as Ollama, so the request, parsing and failure handling stay one tested code path; switching back is configuration |
| **Online mode is a named provider, not "any endpoint"** | Loosen the loopback check | The local check stays strict and the default; going online needs a word you type on purpose (Phase 5 rule "off by default and explicit") |
| **Key from the environment only** | A flag; a config file | Flags show in `ps` and shell history; a file risks a commit. `mailbox/.env` and `.env.*` are already ignored |
| **HTTPS required, no redirects** | Allow HTTP for tests | A key over plain HTTP or forwarded by a redirect leaks; tests inject the endpoint rule through a package-private constructor instead |
| **Second opt-in in the agent service only** | Same rule everywhere; refuse the mailbox entirely | The owner's decision. `PrototypeApp` reads `.eml` files you choose (synthetic sets); the mailbox may show real mail of other people |
| **Record and replay** | Commit nothing; mock in tests only | Makes an online or local run reproducible on any machine and in CI, and gives evidence for the report; exact-match keys make stale replies impossible |
| **No prompt in recordings** | Store the full request | Smaller files and less personal data on disk; the key already identifies the request |
| **Tests ignore the environment** | Read `System.getenv()` everywhere | With `SUPERMAS_LLM_PROVIDER=online` exported, `mvn test` would otherwise call OpenRouter |

## 4. Change to an earlier rule

The plan's Phase 5 rule said "no real mail goes online". On 2026-11-07 the owner allowed it **for the mailbox only, behind a second explicit opt-in** (`SUPERMAS_LLM_ONLINE_MAIL=yes`). The plan now says so. The guide recommends the mailbox's demo workspace while the online model is on.

## 5. Tests

15 new tests; full build **343 tests, 0 failures, 0 skipped** (328 before); mailbox `npm test` 14 of 14.

| Test | Shows |
| --- | --- |
| `LlmSettingsTest` (7) | Template by default; an endpoint alone means local (old flags work); two variables move to another computer; flags beat variables; online needs a key from the environment and HTTPS, has no key flag, and never prints the key; replay needs an existing folder; the online chat path under the base, and bad bases refused |
| `RecordedLlmTest` (3) | Record then replay without calling the model, no prompt in the file; a changed prompt, data or model is an explicit miss; failures are not recorded; the budget still holds on replay |
| `OpenAiCompatibleLlmTest` (+2, now 7) | The online adapter sends `Authorization: Bearer`, the local one sends none; the real online adapter refuses plain HTTP; a 401 carries the engine's message and is not retryable, a 429 is |
| `BlackboardCollaborationTest` (+1) | A full blackboard run recorded through a fake engine is replayed on a fresh board and data folder with the same draft and no engine call (so the composer's prompt is deterministic for the same email and knowledge) |
| `AgentServiceTest` (+1) | `online` refused without the second opt-in (also by the constructor); accepted with it; recording refused |
| `PrototypeAppTest` (+1) | Model from variables or flags; template by default; `parse(args)` ignores the environment |

**Manual checks.** `PrototypeApp` with `SUPERMAS_LLM_PROVIDER=online` and no key stops with "The online provider needs an API key in the environment variable SUPERMAS_LLM_API_KEY". With a dummy key it sent **one request with the synthetic email `en-01` to OpenRouter**, which answered HTTP 401 ("Missing Authentication header"); the board ended `FAILED (CONNECTION ...)` with the message in the control trail. A successful online reply is **not yet verified**: it needs the owner's real key.

## 6. Problems and critical points

1. **Online results are not the project's results.** They overstate quality and understate latency. Every number in the final report must come from the local model, or be labelled with the model and where it ran.
2. **The mailbox opt-in sends other people's mail to a third party** (OpenRouter and the model's provider). It is the owner's informed decision; the setup guide says to turn off provider logging and to prefer the demo workspace.
3. **OpenRouter's answer to the dummy key** ("Missing Authentication header" although the header is sent, as the fake-engine test shows) is unexplained; it may be how OpenRouter reports a malformed key. Check with the real key.
4. **Free models change.** Availability and rate limits of free OpenRouter models change often; a 429 is reported as a retryable failure, but the coordinator does not retry generation yet.
5. **Replay depends on deterministic prompts.** It holds today (tested); anything that adds a timestamp or a random id to a prompt would turn every replay into a miss, visibly.
6. **The interface is still called `LocalLlm`** although the online adapter implements it. Renaming a shared contract needs the team, so the class documentation marks the online adapter as a stand-in instead.
7. **Mailbox's own Ollama path is unchanged.** Without `AGENT_SERVICE_URL`, the mailbox still calls Ollama directly and has no online mode; that is the teammate's code and the UI was not to be touched.

## 7. Next

- With the real key: one run on `synthetic-multi` with recording; check that every email gets a draft, note latency and failures; keep the recording as development evidence.
- The to-do items 2.1-2.3 (local model) stay P1 for delivery; 3.2, 3.3 and 3.7 can be prepared now with the online model and must be re-run locally.
