package org.supermas.llm;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which engine writes the replies, read from environment variables and command-line flags (flags win). Moving to
 * another computer or another model is configuration only.
 *
 * <ul>
 *   <li>{@code template} (default): the fixed template stand-in, no model.</li>
 *   <li>{@code local}: a local OpenAI-compatible engine (Ollama, llama.cpp, LM Studio) on a literal loopback origin.
 *       This is what the project statement requires.</li>
 *   <li>{@code online}: an online OpenAI-compatible API (default OpenRouter) over HTTPS with an API key from the
 *       environment only. <b>A development stand-in</b> (PROTOTYPE-STAND-IN): the email text leaves the computer,
 *       which the statement's data-protection principle forbids for the delivered system.</li>
 *   <li>{@code replay}: replies recorded earlier (see {@link RecordedLlm}), no model and no network.</li>
 * </ul>
 *
 * <p>With {@code local} or {@code online}, a cache folder records every reply for later replay.
 */
public record LlmSettings(Provider provider, URI endpoint, String model, Optional<String> apiKey,
                          Optional<Path> cache, boolean onlineForMail) {
    public enum Provider { TEMPLATE, LOCAL, ONLINE, REPLAY }

    public static final String PROVIDER = "SUPERMAS_LLM_PROVIDER";
    public static final String ENDPOINT = "SUPERMAS_LLM_ENDPOINT";
    public static final String MODEL = "SUPERMAS_LLM_MODEL";
    /** Environment only: a key never goes on a command line, where other users and shell history can see it. */
    public static final String API_KEY = "SUPERMAS_LLM_API_KEY";
    public static final String CACHE = "SUPERMAS_LLM_CACHE";
    /** Environment only: the second opt-in that lets the agent service send real mail to an online model. */
    public static final String ONLINE_MAIL = "SUPERMAS_LLM_ONLINE_MAIL";

    public static final URI DEFAULT_LOCAL = URI.create("http://127.0.0.1:11434");
    public static final URI DEFAULT_ONLINE = URI.create("https://openrouter.ai/api/v1");

    /** Command-line flags and the variable each one overrides. */
    private static final Map<String, String> FLAGS = Map.of("--llm-provider", PROVIDER, "--llm-endpoint", ENDPOINT,
            "--llm-model", MODEL, "--llm-cache", CACHE);

    public LlmSettings {
        endpoint = endpoint == null ? DEFAULT_LOCAL : endpoint;
        model = model == null ? "" : model.strip();
        apiKey = apiKey.filter(key -> !key.isBlank());
    }

    public static LlmSettings template() {
        return new LlmSettings(Provider.TEMPLATE, DEFAULT_LOCAL, "", Optional.empty(), Optional.empty(), false);
    }

    /** True if {@code flag} is one of the model flags, which {@link #from} accepts in {@code flags}. */
    public static boolean isFlag(String flag) {
        return FLAGS.containsKey(flag);
    }

    /**
     * Settings from environment variables, overridden by flags ({@code --llm-provider}, {@code --llm-endpoint},
     * {@code --llm-model}, {@code --llm-cache}). Without a provider, an endpoint means {@code local} (the flags of
     * step 11.4 keep working) and nothing means {@code template}.
     *
     * @throws IllegalArgumentException for an unknown provider, a missing model or key, or an unacceptable endpoint
     */
    public static LlmSettings from(Map<String, String> environment, Map<String, String> flags) {
        Map<String, String> values = new HashMap<>();
        environment.forEach((name, value) -> {
            if (name.startsWith("SUPERMAS_LLM_") && value != null && !value.isBlank()) {
                values.put(name, value.strip());
            }
        });
        flags.forEach((flag, value) -> {
            String name = FLAGS.get(flag);
            if (name == null) {
                throw new IllegalArgumentException("Unknown model option " + flag);
            }
            values.put(name, value.strip());
        });
        Provider provider;
        if (values.containsKey(PROVIDER)) {
            try {
                provider = Provider.valueOf(values.get(PROVIDER).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown model provider " + values.get(PROVIDER)
                        + "; use template, local, online or replay");
            }
        } else {
            provider = values.containsKey(ENDPOINT) ? Provider.LOCAL : Provider.TEMPLATE;
        }
        URI endpoint = values.containsKey(ENDPOINT) ? URI.create(values.get(ENDPOINT))
                : provider == Provider.ONLINE ? DEFAULT_ONLINE : DEFAULT_LOCAL;
        LlmSettings settings = new LlmSettings(provider, endpoint, values.getOrDefault(MODEL, ""),
                Optional.ofNullable(values.get(API_KEY)), Optional.ofNullable(values.get(CACHE)).map(Path::of),
                "yes".equalsIgnoreCase(values.getOrDefault(ONLINE_MAIL, "")));
        settings.validate();
        return settings;
    }

    private void validate() {
        if (provider == Provider.TEMPLATE) {
            if (cache.isPresent()) {
                throw new IllegalArgumentException("The template has nothing to record or replay; drop " + CACHE);
            }
            return;
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("The " + name() + " provider needs a model (--llm-model or " + MODEL + ")");
        }
        switch (provider) {
            case LOCAL -> OpenAiCompatibleLlm.chatEndpoint(endpoint);
            case ONLINE -> {
                OpenAiCompatibleLlm.onlineChatEndpoint(endpoint);
                if (apiKey.isEmpty()) {
                    throw new IllegalArgumentException("The online provider needs an API key in the environment variable "
                            + API_KEY);
                }
            }
            case REPLAY -> {
                if (cache.isEmpty() || !Files.isDirectory(cache.get())) {
                    throw new IllegalArgumentException("Replay needs an existing folder of recorded replies (--llm-cache or "
                            + CACHE + ")");
                }
            }
            default -> { }
        }
    }

    /**
     * The same settings with another recording or replay folder (a benchmark uses one per run). The template keeps
     * none. Replay needs the folder to exist.
     */
    public LlmSettings withCache(Path folder) {
        if (provider == Provider.TEMPLATE) {
            return this;
        }
        LlmSettings settings = new LlmSettings(provider, endpoint, model, apiKey, Optional.of(folder), onlineForMail);
        settings.validate();
        return settings;
    }

    /** The provider in lower case, as it is configured. */
    public String name() {
        return provider.name().toLowerCase(Locale.ROOT);
    }

    /** True if email text leaves this computer. */
    public boolean leavesComputer() {
        return provider == Provider.ONLINE;
    }

    /** One line for logs and the console, never with the key. */
    public String describe() {
        String text = switch (provider) {
            case TEMPLATE -> "template (no model)";
            case LOCAL -> "local model " + model + " at " + endpoint;
            case ONLINE -> "ONLINE model " + model + " at " + endpoint
                    + " (development stand-in: email text is sent to " + endpoint.getHost() + ")";
            case REPLAY -> "replay of recorded replies of " + model + " from " + cache.orElseThrow();
        };
        if (provider != Provider.REPLAY && cache.isPresent()) {
            text += ", recording replies to " + cache.get();
        }
        return text;
    }

    @Override
    public String toString() {
        return "LlmSettings[" + describe() + (apiKey.isPresent() ? ", API key set" : "") + "]";
    }
}
