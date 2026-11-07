# Setting Up the Model

Which engine writes the replies is configuration only (`org.supermas.llm.LlmSettings`): no code changes to move from the template to an online model during development, or to a local model on a stronger computer before delivery. The same settings work for `PrototypeApp` and `AgentService`.

## Providers

| Provider | What writes the reply | Data leaves the computer | When to use |
| --- | --- | --- | --- |
| `template` (default) | A fixed template from the composer's plan | No | Tests, CI, any machine |
| `local` | A local OpenAI-compatible engine (Ollama, llama.cpp, LM Studio) on `127.0.0.1` | No | **The delivered system** (statement §1, §8) |
| `online` | An online OpenAI-compatible API, default OpenRouter | **Yes** | Development only, on synthetic or public emails; a stand-in |
| `replay` | Replies recorded earlier with `local` or `online` | No | Reproducing a run without a model (weak computer, CI) |

## Settings

Flags override environment variables. The API key and the mailbox opt-in exist **only** as environment variables, so they never appear on a command line or in shell history.

| Variable | Flag | Meaning |
| --- | --- | --- |
| `SUPERMAS_LLM_PROVIDER` | `--llm-provider` | `template`, `local`, `online` or `replay`. Without it, an endpoint means `local` (the step 11.4 flags keep working), and nothing means `template` |
| `SUPERMAS_LLM_MODEL` | `--llm-model` | The model name, required for every provider except `template` |
| `SUPERMAS_LLM_ENDPOINT` | `--llm-endpoint` | Default `http://127.0.0.1:11434` for `local` (literal loopback only) and `https://openrouter.ai/api/v1` for `online` (HTTPS only) |
| `SUPERMAS_LLM_API_KEY` | — | The online key, required for `online` |
| `SUPERMAS_LLM_CACHE` | `--llm-cache` | With `local` or `online`: record every reply in this folder. With `replay`: answer from it |
| `SUPERMAS_LLM_ONLINE_MAIL` | — | `yes` lets the **agent service** (the mailbox) use `online`. The second opt-in, see below |

Each run prints the model it uses (`Model: ...`), and every draft's rationale names the engine ("Local model ...", "Online model (development stand-in) ...", "Recorded model ...").

## Now: online, with OpenRouter

1. Create a key at openrouter.ai. In its privacy settings, turn off any option that lets providers log or train on your prompts.
2. Choose an **open-weights model that can also run locally**, of the size you expect to deliver with (for example a Llama 3.2 3B or Qwen 2.5 7B instruct model; check the exact name on openrouter.ai/models). A large proprietary model would hide the problems the final small model will have.
3. Run the prototype on synthetic emails:

```bash
export SUPERMAS_LLM_PROVIDER=online
export SUPERMAS_LLM_MODEL=<model name from openrouter.ai/models>
export SUPERMAS_LLM_API_KEY=<your key>          # never commit it; mailbox/.env is ignored by Git
mvn -q compile exec:java -Dexec.mainClass=org.supermas.prototype.PrototypeApp \
    -Dexec.args="benchmark/data/synthetic-multi --llm-cache runtime/llm-cache"
```

`--llm-cache` records the replies; `runtime/` is ignored by Git. To keep a recording as evidence for the report, copy the folder elsewhere and name the model and date.

### The mailbox (second opt-in)

The agent service serves the desktop mailbox, which can show **real mail**: other people's personal data would go to OpenRouter and its model provider. So the service refuses `online` unless you also set:

```bash
export SUPERMAS_LLM_ONLINE_MAIL=yes
```

It also refuses `--llm-cache`, because a recording would keep replies to real mail on disk. Prefer the mailbox's **demo** workspace while the online model is on.

## Later: a local model on a stronger computer

Install Ollama, pull the same open model, and change two variables:

```bash
ollama pull <model>
export SUPERMAS_LLM_PROVIDER=local
export SUPERMAS_LLM_MODEL=<model>
unset SUPERMAS_LLM_API_KEY SUPERMAS_LLM_ONLINE_MAIL
```

**Measure again.** Results with an online model do not carry over (plan, Phase 5 rule): small local models follow rules less well, drop requests in long prompts, resist injected instructions less, and are far slower. The experiments of statement §15 (relevance, prompt influence, LLM limits) must be run on the delivered local model. Check also that the engine's context window holds the prompt (the input budget is 20,000 characters, about 5,000 tokens): set it explicitly (in Ollama, `num_ctx` in a Modelfile, or `OLLAMA_CONTEXT_LENGTH`), or a long prompt may be cut.

### Without installing on this computer: an SSH tunnel

If you can reach a stronger machine (e.g. a university server) that runs Ollama, forward its port; the adapter still sees a loopback engine, and nothing in the code changes:

```bash
ssh -N -L 11434:127.0.0.1:11434 user@stronger-machine
export SUPERMAS_LLM_PROVIDER=local SUPERMAS_LLM_MODEL=<model>
```

The data then goes to that machine, so the same rule about real mail applies unless it is your own.

## Replay

```bash
mvn -q compile exec:java -Dexec.mainClass=org.supermas.prototype.PrototypeApp \
    -Dexec.args="benchmark/data/synthetic-multi --llm-provider replay --llm-model <recorded model> --llm-cache runtime/llm-cache"
```

A reply is stored under a hash of the model name, the trusted prompt and the untrusted data. Any change to a prompt, the board's data or the model is an explicit miss (the draft fails with "No recorded reply ..."), never a silent fallback to another reply. The files hold the model, the agent and the reply, not the prompt.
