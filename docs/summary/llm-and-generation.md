# Generation and the Local LLM

**Packages:** `org.supermas.llm`, `org.supermas.prototype.TemplateGenerationService`. **Reports:** steps 9.5-9.7, 12; mailbox README (teammate).

## Architecture

```
Composer ─ RolePromptBuilder ─▶ ModelRequest(systemPrompt = trusted, untrustedData = labelled blocks, maxInput)
         ─▶ GenerationService.generate ─┬─ TemplateGenerationService (default: fixed template from the plan, no model)
                                         └─ LlmGenerationService ─▶ LocalLlm ─▶ OpenAiCompatibleLlm ─▶ local engine
                                                                                   POST /v1/chat/completions (loopback)
```

- `OpenAiCompatibleLlm` (Adapter): literal loopback origins only, redirects never followed, system and user messages separate, input budget enforced; connection, timeout, error status, empty or oversize output become explicit failures; exactly one of output or failure.
- `LlmGenerationService`: the output is the reply body only; echoed prompt blocks are refused; subject derived from the email.
- Switch: `--llm-endpoint http://127.0.0.1:11434 --llm-model <name>` on `PrototypeApp` and `AgentService`. Works with Ollama, llama.cpp's server, LM Studio.
- The mailbox (teammate) also calls Ollama directly when the agent service is not configured.

## Choices and alternatives

| Choice | Alternative | Why |
| --- | --- | --- |
| **OpenAI-compatible protocol** | Ollama's native API; a Java inference library | One adapter covers the main local engines; changing engine or model is configuration (statement §8, §13) |
| **Template as default** | Require a model to run | No engine on the development laptop (7 GB RAM); the whole system stays runnable and testable; owner's decision |
| **One model call per draft (composer)** | One call per specialist | Fewer calls on modest hardware; specialists produce structured plans without a model |
| **Trusted/untrusted split in the request** | One prompt string | Email, history, documents and feedback must never override instructions (invariant) |

## Why it complies

Statement §1, §8: local-only inference, no dependency on one model, explicit failures, the prompting strategy and context mechanism are documented choices. The Phase 5 decision to keep an online model only as an off-by-default development exception was never needed.

## Open points

Choose and measure a real model (quality, latency, failure rate) on target hardware; streaming or progress for slow models; lease heartbeats.
