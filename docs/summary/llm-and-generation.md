# Generation and the Local LLM

**Packages:** `org.supermas.llm`, `org.supermas.prototype.TemplateGenerationService`. **Reports:** steps 9.5-9.7, 12, 13; mailbox README (teammate). **Setup:** [`docs/plans/llm-setup.md`](../plans/llm-setup.md).

## Architecture

```
Composer ─ RolePromptBuilder ─▶ ModelRequest(systemPrompt = trusted, untrustedData = labelled blocks, maxInput)
         ─▶ GenerationService.generate ─┬─ TemplateGenerationService (default: fixed template from the plan, no model)
                                         └─ LlmGenerationService ─▶ LocalLlm ─┬─ OpenAiCompatibleLlm ─▶ local engine (loopback)
                                                                               ├─ OpenAiCompatibleLlm.online ─▶ OpenRouter (HTTPS, dev stand-in)
                                                                               └─ RecordedLlm (records any of the above, or replays)
LlmSettings (SUPERMAS_LLM_* / --llm-*) chooses: template | local | online | replay
```

- `OpenAiCompatibleLlm` (Adapter): literal loopback origins only, redirects never followed, system and user messages separate, input budget enforced; connection, timeout, error status, empty or oversize output become explicit failures; exactly one of output or failure.
- `LlmGenerationService`: the output is the reply body only; echoed prompt blocks are refused; subject derived from the email.
- Switch: `LlmSettings` from `SUPERMAS_LLM_*` variables or `--llm-*` flags, on `PrototypeApp` and `AgentService`. `local` works with Ollama, llama.cpp's server, LM Studio; `online` (OpenRouter) is for development, with the key in the environment only and a second opt-in for the mailbox; `RecordedLlm` (Decorator) records replies and replays them without a model.
- The mailbox (teammate) also calls Ollama directly when the agent service is not configured.

## Choices and alternatives

| Choice | Alternative | Why |
| --- | --- | --- |
| **OpenAI-compatible protocol** | Ollama's native API; a Java inference library | One adapter covers the main local engines; changing engine or model is configuration (statement §8, §13) |
| **Template as default** | Require a model to run | No engine on the development laptop (7 GB RAM); the whole system stays runnable and testable; owner's decision |
| **One model call per draft (composer)** | One call per specialist | Fewer calls on modest hardware; specialists produce structured plans without a model |
| **Trusted/untrusted split in the request** | One prompt string | Email, history, documents and feedback must never override instructions (invariant) |
| **Online model during development, local for delivery** (step 13) | Install a 1.5-3 B model on the laptop; an SSH tunnel to a stronger machine | The laptop is too weak for useful iteration; same adapter and protocol, so the switch is two variables; results are re-measured locally |
| **Record and replay** | Mocks only | Reproducible runs without a model, in CI and on weak machines; exact-match keys |

## Why it complies

Statement §1, §8: local-only inference, no dependency on one model, explicit failures, the prompting strategy and context mechanism are documented choices. The Phase 5 rule for an online model as an explicit, off-by-default development exception is implemented in step 13; the owner extended it to the mailbox behind a second opt-in. The delivered configuration must be `local`.

## Open points

Choose and measure a real model (quality, latency, failure rate) on target hardware; streaming or progress for slow models; lease heartbeats.
