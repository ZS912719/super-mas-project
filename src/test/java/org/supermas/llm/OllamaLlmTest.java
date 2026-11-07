package org.supermas.llm;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.supermas.model.Domain.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OllamaLlmTest {
    private HttpServer server;
    private ExecutorService serverWorkers;
    private final AtomicReference<String> received = new AtomicReference<>();
    private String response = "{\"done\":true,\"message\":{\"content\":\"Hello\"}}";
    private int status = 200;
    private long delay;
    private boolean delayBody;
    private final CountDownLatch requestStarted = new CountDownLatch(1);
    private String redirect;
    private final ModelRequest request = new ModelRequest("composer", "Trusted instructions", "Untrusted email", 2000);
    private final OllamaLlm llm = new OllamaLlm();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverWorkers = Executors.newCachedThreadPool();
        server.setExecutor(serverWorkers);
        server.createContext("/api/chat", exchange -> {
            try {
                received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                requestStarted.countDown();
                if (!delayBody) Thread.sleep(delay);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                if (redirect != null) exchange.getResponseHeaders().add("Location", redirect);
                exchange.sendResponseHeaders(status, bytes.length);
                if (delayBody) Thread.sleep(delay);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); serverWorkers.shutdownNow(); }
    private ModelConfig config(Duration timeout) {
        return new ModelConfig("test-model", URI.create("http://127.0.0.1:" + server.getAddress().getPort()), timeout, 1000);
    }
    private ModelResponse call() { return llm.complete(request, config(Duration.ofSeconds(2))); }
    private void assertFailure(FailureKind kind) {
        ModelResponse result = call();
        assertTrue(result.output().isEmpty());
        assertEquals(kind, result.failure().orElseThrow().kind());
    }
    @Test void successfulGenerationPreservesInstructionBoundary() {
        ModelResponse result = call();
        assertEquals("Hello", result.output().orElseThrow());
        assertTrue(result.failure().isEmpty());
        var payload = JsonParser.parseString(received.get()).getAsJsonObject();
        assertFalse(payload.get("stream").getAsBoolean());
        assertEquals("test-model", payload.get("model").getAsString());
        assertEquals("system", payload.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
        assertEquals(request.untrustedData(), payload.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
    }
    @Test void unavailableServer() { server.stop(0); assertFailure(FailureKind.CONNECTION); }
    @Test void timeout() { delay = 1000; assertEquals(FailureKind.TIMEOUT, llm.complete(request, config(Duration.ofMillis(100))).failure().orElseThrow().kind()); }
    @Test void timeoutIncludesSlowBody() { delayBody = true; timeout(); }
    @Test void invalidJson() { response = "not json"; assertFailure(FailureKind.INVALID_OUTPUT); }
    @Test void emptyOutput() { response = "{\"done\":true,\"message\":{\"content\":\"  \"}}"; assertFailure(FailureKind.INVALID_OUTPUT); }
    @Test void incompleteResponse() { response = "{\"done\":false}"; assertFailure(FailureKind.INVALID_OUTPUT); }
    @Test void wrongContentType() { response = "{\"done\":true,\"message\":{\"content\":42}}"; assertFailure(FailureKind.INVALID_OUTPUT); }
    @Test void httpFailure() { status = 503; assertFailure(FailureKind.CONNECTION); }
    @Test void redirectIsNotFollowed() throws Exception {
        AtomicReference<Boolean> followed = new AtomicReference<>(false);
        HttpServer destination = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        destination.createContext("/", exchange -> {
            followed.set(true);
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        destination.start();
        try {
            redirect = "http://127.0.0.1:" + destination.getAddress().getPort() + "/";
            status = 307;
            assertFailure(FailureKind.CONNECTION);
            assertFalse(followed.get());
        } finally { destination.stop(0); }
    }
    @Test void invalidConfiguration() {
        var bad = new ModelConfig("", config(Duration.ofSeconds(1)).endpoint(), Duration.ZERO, 0);
        assertEquals(FailureKind.INVALID_OUTPUT, llm.complete(request, bad).failure().orElseThrow().kind());
        assertNull(received.get());
    }
    @Test void remoteAndAmbiguousEndpointsRejectedBeforeSending() {
        for (String address : new String[]{"http://example.com:11434", "http://127.0.0.1.example.com:11434", "http://192.168.1.2:11434",
                "http://user@127.0.0.1:11434", "http://127.0.0.1:11434/path", "http://127.0.0.1:11434?x=1", "https://localhost:11434"}) {
            var bad = new ModelConfig("model", URI.create(address), Duration.ofSeconds(1), 100);
            assertEquals(FailureKind.INVALID_OUTPUT, llm.complete(request, bad).failure().orElseThrow().kind(), address);
        }
        assertNull(received.get());
    }
    @Test void oversizedOutputRejected() { response = "{\"done\":true,\"message\":{\"content\":\"" + "x".repeat(1001) + "\"}}"; assertFailure(FailureKind.INVALID_OUTPUT); }
    @Test void transportLimitIsInvalidOutputAndNotRetryable() {
        response = "x".repeat(4 * 1024 * 1024 + 1);
        ModelResponse result = call();
        assertTrue(result.output().isEmpty());
        Failure failure = result.failure().orElseThrow();
        assertEquals(FailureKind.INVALID_OUTPUT, failure.kind());
        assertFalse(failure.retryable());
        assertTrue(failure.message().contains("transport limit"));
    }
    @Test void inputBudgetCheckedBeforeNetwork() {
        var small = new ModelRequest("a", "system", "data", 2);
        assertEquals(FailureKind.BUDGET_EXHAUSTED, llm.complete(small, config(Duration.ofSeconds(1))).failure().orElseThrow().kind());
        assertNull(received.get());
    }
    @Test void interruptionPreserved() throws Exception {
        delay = 2000;
        AtomicReference<ModelResponse> result = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread worker = new Thread(() -> { result.set(call()); interrupted.set(Thread.currentThread().isInterrupted()); });
        worker.start();
        assertTrue(requestStarted.await(1, TimeUnit.SECONDS), "Interrupt only after the server receives the request");
        worker.interrupt();
        worker.join(3000);
        assertFalse(worker.isAlive());
        assertEquals(FailureKind.INTERRUPTED, result.get().failure().orElseThrow().kind());
        assertTrue(interrupted.get());
    }
}
