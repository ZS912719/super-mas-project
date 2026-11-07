package org.supermas.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.ModelConfig;
import org.supermas.model.Domain.ModelRequest;
import org.supermas.model.Domain.ModelResponse;

/**
 * Adapter from {@link LocalLlm} to the OpenAI-compatible chat API ({@code POST .../chat/completions}), which local
 * engines such as Ollama, llama.cpp's server and LM Studio serve, and online services such as OpenRouter too.
 * Changing the model or engine is configuration ({@link ModelConfig}, {@link LlmSettings}): no agent depends on
 * this class.
 *
 * <p>{@link #OpenAiCompatibleLlm() The local adapter} is local-only by construction: the endpoint must be a literal
 * loopback HTTP origin, and no credential is ever sent. {@link #online(String) The online adapter} is a development
 * stand-in (PROTOTYPE-STAND-IN, see {@link LlmSettings}): HTTPS only, the key as a bearer token. Neither follows
 * redirects, so a key can never be forwarded elsewhere. The trusted system prompt and the untrusted data travel as
 * separate messages. Connection failure, timeout, an error status and invalid output each become an explicit
 * {@link Failure}; a response holds exactly one of output or failure.
 */
public final class OpenAiCompatibleLlm implements LocalLlm {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "[::1]");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ERROR = 200;

    /** Pause before the one retry of the online adapter. */
    static final long RETRY_PAUSE_MILLIS = 2_000;

    private final HttpClient client;
    private final UnaryOperator<URI> target;
    private final Optional<String> apiKey;
    private final String label;
    private final int attempts;

    /** The local adapter: loopback only, no credential. */
    public OpenAiCompatibleLlm() {
        this(defaultClient(), OpenAiCompatibleLlm::chatEndpoint, Optional.empty(), "local model", 1);
    }

    /** The online adapter, a development stand-in: HTTPS only, {@code apiKey} sent as a bearer token. */
    public static OpenAiCompatibleLlm online(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("The online model needs an API key");
        }
        return new OpenAiCompatibleLlm(defaultClient(), OpenAiCompatibleLlm::onlineChatEndpoint, Optional.of(apiKey),
                "online model", 2);
    }

    /**
     * For tests: any endpoint rule, so a fake engine on loopback can stand in for an HTTPS service.
     *
     * @param attempts 1 for no retry; the online adapter retries once a retryable failure (timeout, rate limit,
     *                 server error, lost connection), because a shared service fails transiently
     */
    OpenAiCompatibleLlm(HttpClient client, UnaryOperator<URI> target, Optional<String> apiKey, String label, int attempts) {
        this.client = client;
        this.target = target;
        this.apiKey = apiKey;
        this.label = label;
        this.attempts = attempts;
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** The chat endpoint for a configured origin, or an exception if the origin is not a literal loopback one. */
    public static URI chatEndpoint(URI origin) {
        if (origin == null || !"http".equals(origin.getScheme()) || origin.getHost() == null
                || !LOOPBACK.contains(origin.getHost()) || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || !(origin.getPath() == null || origin.getPath().isEmpty()
                || origin.getPath().equals("/"))) {
            throw new IllegalArgumentException("The model endpoint must be a literal loopback HTTP origin, such as "
                    + "http://127.0.0.1:11434");
        }
        return origin.resolve("/v1/chat/completions");
    }

    /**
     * The chat endpoint under an online API base such as {@code https://openrouter.ai/api/v1}, or an exception if
     * the base is not plain HTTPS (no credentials, query or fragment in the URL).
     */
    public static URI onlineChatEndpoint(URI base) {
        if (base == null || !"https".equals(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("The online model endpoint must be an HTTPS API base without credentials, "
                    + "such as " + LlmSettings.DEFAULT_ONLINE);
        }
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        return URI.create("https://" + base.getRawAuthority() + path + "/chat/completions");
    }

    @Override
    public ModelResponse complete(ModelRequest request, ModelConfig config) {
        ModelResponse response = attempt(request, config);
        for (int retry = 1; retry < attempts && response.failure().map(Failure::retryable).orElse(false); retry++) {
            try {
                Thread.sleep(RETRY_PAUSE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return failure(FailureKind.INTERRUPTED, "Interrupted while waiting to retry the " + label, false);
            }
            response = attempt(request, config);
        }
        return response;
    }

    private ModelResponse attempt(ModelRequest request, ModelConfig config) {
        URI url;
        try {
            url = target.apply(config.endpoint());
        } catch (IllegalArgumentException e) {
            return failure(FailureKind.CONNECTION, e.getMessage(), false);
        }
        int input = request.systemPrompt().length() + request.untrustedData().length();
        if (input > request.maxInputCharacters()) {
            return failure(FailureKind.BUDGET_EXHAUSTED, "The model input has " + input + " characters, over the budget of "
                    + request.maxInputCharacters(), false);
        }
        ObjectNode body = JSON.createObjectNode();
        body.put("model", config.model());
        body.put("stream", false);
        body.put("temperature", 0.2);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        messages.addObject().put("role", "user").put("content", request.untrustedData());
        HttpResponse<String> response;
        try {
            HttpRequest.Builder http = HttpRequest.newBuilder(url).timeout(config.timeout())
                    .header("Content-Type", "application/json");
            apiKey.ifPresent(key -> http.header("Authorization", "Bearer " + key));
            // The request timeout bounds only the wait for the response headers. A service that sends the headers
            // and then stalls the body would hold the task until its lease expires (step 13, run A), so the
            // whole exchange gets the same deadline.
            CompletableFuture<HttpResponse<String>> pending = client.sendAsync(
                    http.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            try {
                response = pending.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                pending.cancel(true);
                return failure(FailureKind.TIMEOUT, "The " + label + " did not answer within " + config.timeout(), true);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof HttpTimeoutException) {
                    return failure(FailureKind.TIMEOUT, "The " + label + " did not answer within " + config.timeout(), true);
                }
                if (cause instanceof ConnectException) {
                    return failure(FailureKind.CONNECTION, "Cannot reach the " + label + " at " + config.endpoint(), true);
                }
                return failure(FailureKind.CONNECTION, "The " + label + " request failed: "
                        + (cause == null ? e.getMessage() : cause.getMessage()), true);
            }
        } catch (IOException e) {
            return failure(FailureKind.CONNECTION, "The " + label + " request could not be built: " + e.getMessage(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(FailureKind.INTERRUPTED, "Interrupted while waiting for the " + label, false);
        }
        if (response.statusCode() != 200) {
            // 429 (rate limit) and 5xx are worth a retry; anything else (bad key, unknown model) is not.
            boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
            return failure(FailureKind.CONNECTION, "The " + label + " answered HTTP " + response.statusCode()
                    + errorMessage(response.body()), retryable);
        }
        try {
            JsonNode content = JSON.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                return failure(FailureKind.INVALID_OUTPUT, "The " + label + " returned no text", false);
            }
            String text = content.asText().strip();
            if (text.length() > config.maxOutputCharacters()) {
                return failure(FailureKind.INVALID_OUTPUT, "The " + label + " returned " + text.length()
                        + " characters, over the limit of " + config.maxOutputCharacters(), false);
            }
            return new ModelResponse(Optional.of(text), Optional.empty());
        } catch (IOException e) {
            return failure(FailureKind.INVALID_OUTPUT, "The " + label + " returned invalid JSON", false);
        }
    }

    /** The engine's own error message ({@code error.message}), shortened, or nothing. */
    private static String errorMessage(String body) {
        try {
            JsonNode message = JSON.readTree(body == null ? "" : body).path("error").path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                String text = message.asText().strip();
                return ": " + (text.length() > MAX_ERROR ? text.substring(0, MAX_ERROR) + "..." : text);
            }
        } catch (IOException e) {
            // not JSON: the status alone is the message
        }
        return "";
    }

    private static ModelResponse failure(FailureKind kind, String message, boolean retryable) {
        return new ModelResponse(Optional.empty(), Optional.of(new Failure(kind, message, retryable)));
    }
}
