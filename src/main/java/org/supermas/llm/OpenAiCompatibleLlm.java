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
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.ModelConfig;
import org.supermas.model.Domain.ModelRequest;
import org.supermas.model.Domain.ModelResponse;

/**
 * Adapter from {@link LocalLlm} to the OpenAI-compatible chat API ({@code POST /v1/chat/completions}), which local
 * engines such as Ollama, llama.cpp's server and LM Studio serve. Changing the model or engine is configuration
 * ({@link ModelConfig}): no agent depends on this class.
 *
 * <p>Local-only by construction: the endpoint must be a literal loopback HTTP origin; redirects are never
 * followed. The trusted system prompt and the untrusted data travel as separate messages. Connection failure,
 * timeout, an error status and invalid output each become an explicit {@link Failure}; a response holds exactly one
 * of output or failure.
 */
public final class OpenAiCompatibleLlm implements LocalLlm {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "[::1]");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client;

    public OpenAiCompatibleLlm() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    OpenAiCompatibleLlm(HttpClient client) {
        this.client = client;
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

    @Override
    public ModelResponse complete(ModelRequest request, ModelConfig config) {
        URI target;
        try {
            target = chatEndpoint(config.endpoint());
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
            response = client.send(HttpRequest.newBuilder(target).timeout(config.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            return failure(FailureKind.TIMEOUT, "The local model did not answer within " + config.timeout(), true);
        } catch (ConnectException e) {
            return failure(FailureKind.CONNECTION, "Cannot reach the local model at " + config.endpoint(), true);
        } catch (IOException e) {
            return failure(FailureKind.CONNECTION, "Local model request failed: " + e.getMessage(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(FailureKind.INTERRUPTED, "Interrupted while waiting for the local model", false);
        }
        if (response.statusCode() != 200) {
            return failure(FailureKind.CONNECTION, "The local model answered HTTP " + response.statusCode(), false);
        }
        try {
            JsonNode content = JSON.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                return failure(FailureKind.INVALID_OUTPUT, "The local model returned no text", false);
            }
            String text = content.asText().strip();
            if (text.length() > config.maxOutputCharacters()) {
                return failure(FailureKind.INVALID_OUTPUT, "The local model returned " + text.length()
                        + " characters, over the limit of " + config.maxOutputCharacters(), false);
            }
            return new ModelResponse(Optional.of(text), Optional.empty());
        } catch (IOException e) {
            return failure(FailureKind.INVALID_OUTPUT, "The local model returned invalid JSON", false);
        }
    }

    private static ModelResponse failure(FailureKind kind, String message, boolean retryable) {
        return new ModelResponse(Optional.empty(), Optional.of(new Failure(kind, message, retryable)));
    }
}
