package org.supermas.llm;

import com.google.gson.*;
import org.supermas.model.Domain.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/** Non-streaming local transport. Call on a worker thread; interruption cancels the exchange. */
public final class OllamaLlm implements LocalLlm {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private final Gson json = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(new ProxySelector() {
                public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
            }).build();

    @Override
    public ModelResponse complete(ModelRequest request, ModelConfig config) {
        try {
            validateConfig(config);
        } catch (IllegalArgumentException | ArithmeticException e) {
            return failure(FailureKind.INVALID_OUTPUT, "Invalid local model configuration.", false);
        }
        CompletableFuture<HttpResponse<byte[]>> exchange = null;
        try {
            if (request == null || request.systemPrompt() == null || request.untrustedData() == null
                    || request.maxInputCharacters() <= 0) {
                return failure(FailureKind.INVALID_OUTPUT, "Invalid model request.", false);
            }
            if ((long) request.systemPrompt().length() + request.untrustedData().length()
                    > request.maxInputCharacters()) {
                return failure(FailureKind.BUDGET_EXHAUSTED, "Model input exceeds its character budget.", false);
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("model", config.model());
            payload.addProperty("stream", false);
            JsonArray messages = new JsonArray();
            messages.add(message("system", request.systemPrompt()));
            messages.add(message("user", request.untrustedData()));
            payload.add("messages", messages);
            // The shared contract specifies characters, not tokens; do not invent a token conversion.
            HttpRequest call = HttpRequest.newBuilder(localEndpoint(config.endpoint()).resolve("/api/chat"))
                    .timeout(config.timeout()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toJson(payload))).build();
            exchange = http.sendAsync(call, ignored -> new LimitedBody());
            HttpResponse<byte[]> response = exchange.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                return failure(FailureKind.CONNECTION, "Local engine returned HTTP " + response.statusCode() + ".",
                        response.statusCode() >= 500);
            }
            return decode(response.body(), config);
        } catch (InterruptedException e) {
            if (exchange != null) exchange.cancel(true);
            Thread.currentThread().interrupt();
            return failure(FailureKind.INTERRUPTED, "Local inference was cancelled.", false);
        } catch (TimeoutException e) {
            if (exchange != null) exchange.cancel(true);
            return failure(FailureKind.TIMEOUT, "Local inference timed out.", true);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return failure(FailureKind.INVALID_OUTPUT, "Local engine response exceeds the transport limit.", false);
            }
            return failure(cause instanceof HttpTimeoutException ? FailureKind.TIMEOUT : FailureKind.CONNECTION,
                    "Local engine connection failed.", true);
        }
    }

    private ModelResponse decode(byte[] bytes, ModelConfig config) {
        try {
            JsonObject result = json.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
            if (result == null || !result.has("done") || !result.get("done").isJsonPrimitive()
                    || !result.getAsJsonPrimitive("done").isBoolean() || !result.get("done").getAsBoolean()
                    || (result.has("done_reason") && "length".equals(result.get("done_reason").getAsString()))) {
                return failure(FailureKind.INVALID_OUTPUT, "Local engine returned an incomplete response.", false);
            }
            JsonElement message = result.get("message");
            if (message == null || !message.isJsonObject()) {
                return failure(FailureKind.INVALID_OUTPUT, "Local engine returned an invalid message.", false);
            }
            JsonElement content = message.getAsJsonObject().get("content");
            if (content == null || !content.isJsonPrimitive() || !content.getAsJsonPrimitive().isString()
                    || content.getAsString().isBlank()) {
                return failure(FailureKind.INVALID_OUTPUT, "Local engine returned empty or invalid content.", false);
            }
            String output = content.getAsString();
            if (output.length() > config.maxOutputCharacters()) {
                return failure(FailureKind.INVALID_OUTPUT, "Local engine output exceeds its character limit.", false);
            }
            return new ModelResponse(Optional.of(output), Optional.empty());
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            return failure(FailureKind.INVALID_OUTPUT, "Malformed local engine response.", false);
        }
    }

    public static void validateConfig(ModelConfig config) {
        if (config == null || config.model() == null || config.model().isBlank()
                || config.timeout() == null || config.timeout().toMillis() < 1
                || config.timeout().compareTo(Duration.ofMinutes(10)) > 0
                || config.maxOutputCharacters() < 1 || config.maxOutputCharacters() > 1_000_000) {
            throw new IllegalArgumentException("Model, timeout (1 ms–10 minutes) and output limit (1–1000000) are required.");
        }
        localEndpoint(config.endpoint());
    }

    private static URI localEndpoint(URI endpoint) {
        if (endpoint == null || !"http".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || endpoint.getPath() == null || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                || endpoint.getPort() < 1 || endpoint.getPort() > 65535) {
            throw new IllegalArgumentException("Use an HTTP loopback base URL with an explicit port.");
        }
        String host = endpoint.getHost();
        if (host == null || !List.of("localhost", "127.0.0.1", "[::1]").contains(host)) {
            throw new IllegalArgumentException("Only localhost, 127.0.0.1 and [::1] are permitted.");
        }
        // Pin localhost to a literal address so no DNS or hosts-file override can redirect it.
        return URI.create("http://" + (host.equals("localhost") ? "127.0.0.1" : host) + ":" + endpoint.getPort());
    }

    private JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static ModelResponse failure(FailureKind kind, String message, boolean retryable) {
        return new ModelResponse(Optional.empty(), Optional.of(new Failure(kind, message, retryable)));
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > MAX_RESPONSE_BYTES) {
                    body.completeExceptionally(new IllegalArgumentException("Response too large."));
                    subscription.cancel();
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
