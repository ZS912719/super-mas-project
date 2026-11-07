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
| `llm.LlmGenerationService` | Names the engine in its rationale ("Online model (development stand-in) ...", "Recorded model ..."). *Correction (§5b, fix 2):* the composer and the specialists dropped that rationale until fix 2, so drafts did not name the engine before it |
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

15 new tests; full build **343 tests, 0 failures, 0 skipped** (328 before); 345 after the prompt fix of §5b, 346 after fix 2; mailbox `npm test` 14 of 14.

| Test | Shows |
| --- | --- |
| `LlmSettingsTest` (7) | Template by default; an endpoint alone means local (old flags work); two variables move to another computer; flags beat variables; online needs a key from the environment and HTTPS, has no key flag, and never prints the key; replay needs an existing folder; the online chat path under the base, and bad bases refused |
| `RecordedLlmTest` (3) | Record then replay without calling the model, no prompt in the file; a changed prompt, data or model is an explicit miss; failures are not recorded; the budget still holds on replay |
| `OpenAiCompatibleLlmTest` (+2, now 7) | The online adapter sends `Authorization: Bearer`, the local one sends none; the real online adapter refuses plain HTTP; a 401 carries the engine's message and is not retryable, a 429 is |
| `BlackboardCollaborationTest` (+1) | A full blackboard run recorded through a fake engine is replayed on a fresh board and data folder with the same draft and no engine call (so the composer's prompt is deterministic for the same email and knowledge) |
| `AgentServiceTest` (+1) | `online` refused without the second opt-in (also by the constructor); accepted with it; recording refused |
| `PrototypeAppTest` (+1) | Model from variables or flags; template by default; `parse(args)` ignores the environment |

**Manual checks.** `PrototypeApp` with `SUPERMAS_LLM_PROVIDER=online` and no key stops with "The online provider needs an API key in the environment variable SUPERMAS_LLM_API_KEY". With a dummy key it sent **one request with the synthetic email `en-01` to OpenRouter**, which answered HTTP 401 ("Missing Authentication header"); the board ended `FAILED (CONNECTION ...)` with the message in the control trail. A successful online reply is **not yet verified**: it needs the owner's real key.

## 5b. First online run and a prompt fix

**Run 1** (owner's key, `meta-llama/llama-3.2-3b-instruct` on OpenRouter, `synthetic-dev/en-01.eml`): the connection works (so the "Missing Authentication header" of the dummy key was only the dummy key); the board ended `AWAITING_REVIEW` with the composer's draft from the model. Steps: request splitter (1 request), thread context, missing information (nothing missing), student contribution (0.87, 3 excerpts), composer.

| Check | Result |
| --- | --- |
| Invented facts | **None.** Medical certificate and course leader from `student-faq/extensions.md`, deadline from the email, office hours from `general-info/contacts.md` |
| Addressee | **Wrong.** "Dear Prof. Martin": the model wrote *to* the recipient instead of *as* the recipient to Sarah Klein |
| Content | **Muddled.** "contact the student office ... to book a meeting with you" combines two documents; it omits that the course leader decides and that extensions are at most seven days |

**Cause, partly ours.** The prompt never said who sent the email or on whose behalf the reply is written: the data had the subject, the text (which opens "Dear Prof. Martin,"), the plan and the excerpts only, and the common rules did not state the perspective. A 3 B model copied the email's own salutation.

**Fix** (approved by the owner the same day):

1. `RolePromptBuilder` opens the data with an `EMAIL ADDRESSES` block: the sender ("who receives the reply") and the recipients ("on whose behalf the reply is written"). Addresses are untrusted like the rest of the email: block markers are broken as everywhere, line breaks are collapsed so an address cannot add a line, and a missing one is "unknown". Both callers (`ReplyWork`, `ComposerAgent`) pass `Email.sender()` and `recipients()`. Only addresses exist (`MimeEmailParser` keeps no display names), so the model takes the sender's name from the signature.
2. `prompts/common.txt` (all roles): write as the recipient to the sender; greet the sender by the name they sign with, otherwise neutrally; never greet the recipient or the person the email is addressed to; close without a name, because the person who sends the reply signs it.
3. `prompts/composer.txt`: keep each fact as its source states it, including who decides and any limit; never combine statements from different sources into a new one.

The prompt versions (hashes recorded with every plan and draft) change accordingly, and recordings made before the fix no longer replay, by design. Tests: `RoleAgentTest` +2 (the block and the rules reach the model; an address cannot open a block or add a line). Full build 345 tests, 0 failures.

**Run 2** (same email and model, after the fix; run on the data folder of run 1):

| Check | Result |
| --- | --- |
| Addressee | **Fixed.** "Dear Sarah," |
| Invented facts | **None** |
| Key facts | **Present now:** medical certificate, the course leader decides, at most seven days |
| Relevance | **Poor.** Every excerpt is dumped into the reply: room booking, exam dates, resits. The sender's own deadline and student number are repeated back to her |
| Closing | **Rule broken:** "[Your Name]" placeholder, although the prompt says no name |
| Helpfulness | **Weak.** It explains how to request an extension to someone who has just requested one, instead of asking her for the medical certificate |

Two causes are ours, one is the model's:

1. The composer's prompt never says to leave out context that answers no request (prompt gap).
2. The missing-information agent reported nothing missing, although the policy requires a certificate that the email lacks: the rule check looks at the role's required fields, not at requirements stated in the knowledge (system gap, not the model's; to-do 3.9).
3. The placeholder despite an explicit rule is the 3 B model's weakness in following rules.

**Fix 2** (approved by the owner the same day):

1. `prompts/composer.txt`: use only the facts that help answer a request or ask for what is missing; leave out all other context even when true; do not repeat back what the sender wrote (own deadline, student number).
2. `prompts/common.txt`: end with the line "Best regards," and nothing after it; never a placeholder such as [Your Name] or [Signature].
3. `LlmGenerationService` removes a bracketed placeholder (up to 40 characters) that ends the reply, on its own line or right after the closing's comma, and notes "removed the placeholder [...]" in its rationale. A bracket inside a sentence ("annex [A]") or a placeholder followed by more text stays; a reply that is only a placeholder is invalid output. Code catches what a small model will keep doing despite the rule.
4. **Found while doing it:** `ComposerAgent` and `ReplyWork` built their own draft explanation and dropped the generation service's rationale, so the engine label of §2 never reached a draft. Both now append it (`ReplyWork.engineNote`).

Tests: `OpenAiCompatibleLlmTest` +1 (placeholder removed and noted, same-line form, legitimate brackets kept, placeholder-only refused); `BlackboardCollaborationTest` asserts the engine note on the composed draft for a local and a recorded model. Full build 346 tests, 0 failures.

**Run 3** (after fix 2, same email and model, again on the data folder of run 1). Draft explanation: "Composed from [student 0.87 → requests [1]]; plan reply-plan:composer:1, prompt f0fc3e8d2cac, 3 excerpts; Online model (development stand-in) meta-llama/llama-3.2-3b-instruct; removed the placeholder [Your Name]".

| Check | Result |
| --- | --- |
| Addressee and closing | **Right.** "Dear Sarah," ... "Best regards,"; the model wrote "[Your Name]" again and the guard removed it, as the explanation shows |
| Irrelevant context | **Gone** (no exams, resits, rooms) |
| Repeating the sender's facts | **Still there** (deadline, student number), against the new rule |
| Facts | **Wrong, a regression.** "Please contact the student office to request an extension" (the policy says the course leader decides and the request goes to the course leader) and "can be contacted through the facilities portal" (the portal is for booking rooms): two sources merged into a false statement, against the explicit rule. Certificate and the seven-day limit are missing |

**Reading.** Each prompt change fixed what it targeted, and the 3 B model then broke something else, including an explicit rule. Three runs on one email are not evidence either way; tuning the prompt further on `en-01` would repeat the mistake avoided for the contribution threshold (tuning on the test item). Next, in order: the same email with a larger open model (configuration only), to separate the model's limits from the prompt's; then the whole `synthetic-multi` set recorded, scored with the rubric of to-do 3.2; a fact check of the reply against the excerpts (a critic, statement §20) if invented or merged facts persist; to-do 3.9 for the missing certificate.


## 5c. A reply-quality benchmark (approved 2026-11-07)

Three runs on one email cannot say whether the model or the system is at fault; the input of run 3, read from its board, showed both (two of three excerpts were unrelated, the plan presented the sender's own details as facts to use, and the missing certificate was not detected). The owner approved a 2×2 benchmark: 3 B and 8 B models, each on the system as it is and after the system fixes. Method: [`docs/benchmark/reply-quality.md`](../benchmark/reply-quality.md).

1. **Frozen expectations** (`reply-gold/v1`, one `<email>.reply.json` per email, 36 in all, commit `451a654`, before any model run): language, whether a reply is expected (10 of the 24 dev emails need none: notices, broadcasts, thank-you notes, the owner's own sent mail), whom to greet, facts from the six knowledge documents to mention, what to ask for, and forbidden phrases (promises or decisions for the owner, the sender's own data repeated back, unrelated knowledge). Written by the AI assistant; **human review pending**. A first draft had over-broad prohibitions ("the room is" would have flagged "the room is not yet announced"; French "accordée" the policy sentence itself); they were narrowed before the freeze.
2. **`ReplyChecks`** (test scope): keyword checks with case, accent and apostrophe folding; greeting line; language by stop words; an "ask" is a sentence with the term that is a question or carries an asking cue; placeholders and staff-only text are violations on every reply.
3. **`ReplyQualityBenchmark`** (test scope): one model configuration per run, fresh data per set and repeat, dev once and multi twice by default, every reply recorded for replay; `results.md`, `drafts.md`, `scores.jsonl`; refuses an existing label folder.
4. `LlmSettings.withCache` gives each run its own recording folder.

Tests (`ReplyChecksTest`, 6): the three real drafts of runs 1-3 score as read by hand (run 1 wrong greeting; run 2 student number and placeholder, the certificate stated but not asked for; run 3 decider and limit missing), and a correct reply is clean; French, accents and the staff exception; every email has its checks; a template run writes all outputs and a second run with the same label is refused.

**A broken commit, fixed.** The Phase 0 dataset loader (`benchmark.Dataset`) refuses unknown files in a benchmark folder, so the commit of the checks (`451a654`) made 10 metadata tests fail; the full build was not run before that commit. The loader now accepts a `.reply.json` beside its email and leaves it out of the dataset fingerprint, so the hashes cited by earlier results stay valid (`DatasetReplyChecksTest`). Full build 353 tests, 0 failures.

**Template floor** (no model): 0 of 22 replies without violation (it greets "Hello," and leaves a placeholder by construction), 3 of 21 facts, all from quoting the request text. Runs A and B are the owner's, with the key.

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
