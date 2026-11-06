# Step 9.6: Optional LLM Extraction of Requests

## Outcome

`LlmRequestExtractor` is an alternative to the rule extractor for finding what the sender asks for. It is off by default: `RoleServices.planOnly()` uses rules, and the LLM variant is chosen with `services.withRequests(new LlmRequestExtractor(llm, model))`. Five tests pass, covering the rule extractor in English and French and every LLM failure path. No real model was run (no adapter exists, and the team owns it).

## How it works

| Extractor | Behaviour |
| --- | --- |
| `RuleRequestExtractor` (default) | Splits the new text into sentences; keeps those ending in `?` or containing fixed English/French request phrases ("could you", "please", "pourriez-vous", "merci de"…); skips quoted lines; at most 5, each at most 300 characters; copied verbatim |
| `LlmRequestExtractor` | Sends a fixed trusted prompt and the email text as untrusted data (at most 6,000 characters in total) through `LocalLlm`. Accepts only `{"requests": [...]}` with at most 5 entries, **each found word for word in the email** (whitespace aside). Otherwise, or on any failure or exception, it uses the rules and reports source `rules-after-llm-failure` |

The source of the requests is written into the plan (`requestSource`), so a draft can be traced to rules or model.

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| The model may only quote, and code verifies it | The plan's design (Phase 5): require a verbatim quote and let code check it. An invented request such as "please approve my extension" is rejected | Trusting the model's paraphrase |
| One bad quote rejects the whole answer | A model that invents once has gone off script, as in the LLM missing-information check | Dropping only the bad quote |
| Off by default | The plan cuts LLM features first, and the laptop has no local model | On by default |

## Critical points

- **This variant sends email text to the model**, unlike the LLM missing-information check, which sends only labels. With the development online LLM, only synthetic and public data may go through it (plan section 3).
- **A verified quote is still untrusted text.** A quoted request can contain an instruction; it goes into the untrusted data block, like everything else from the email.
- **Not benchmarked.** Rules against LLM extraction needs labelled requests, which do not exist yet.
