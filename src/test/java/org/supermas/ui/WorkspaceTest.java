package org.supermas.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.supermas.ui.demo.DemoWorkspace;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {
    private MailApplication application;
    private Stage stage;
    private Scene scene;
    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }
    @AfterEach void close() throws Exception { fx(() -> { application.stop(); stage.close(); return null; }); }
    @AfterAll static void stopToolkit() { Platform.exit(); }
    private static <T> T fx(Callable<T> call) throws Exception {
        FutureTask<T> task = new FutureTask<>(call); Platform.runLater(task); return task.get(5, TimeUnit.SECONDS);
    }
    private Button button(String text) {
        for (var node : scene.getRoot().lookupAll(".button")) {
            if (node instanceof Button button && button.getText().equals(text)) return button;
        }
        throw new AssertionError("Button not found: " + text);
    }
    private void open(MailApplication app) {
        application = app; stage = new Stage();
        scene = new Scene(app.workspace(new DemoWorkspace()), 1180, 760);
        stage.setScene(scene); stage.show(); scene.getRoot().applyCss(); scene.getRoot().layout();
    }
    private void awaitFinished() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (fx(() -> button("Compose proposal").isDisabled()) && System.nanoTime() < deadline) Thread.sleep(20);
        assertFalse(fx(() -> button("Compose proposal").isDisabled()));
    }
    @Test void demoComposesAndNeverEnablesAuthoritativeApproval() throws Exception {
        fx(() -> { open(new MailApplication()); button("Compose proposal").fire(); return null; });
        awaitFinished();
        fx(() -> {
            assertTrue(button("Approve — unavailable").isDisabled());
            assertTrue(draftBody().getText().contains("The course starts on Monday"));
            // Enable manual capture with: mvn -Dsupermas.ui.screenshot=true -Dtest=WorkspaceTest test
            if (Boolean.getBoolean("supermas.ui.screenshot")) {
                var image = scene.snapshot(null);
                BufferedImage png = new BufferedImage((int)image.getWidth(), (int)image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
                    png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                ImageIO.write(png, "png", Path.of("target", "ui-smoke.png").toFile());
            }
            button("Demo: reject proposal").fire();
            assertTrue(statusText().contains("Nothing was persisted"));
            return null;
        });
    }
    @Test void slowInferenceDoesNotBlockUiOrOverwriteEdits() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean onUiThread = new AtomicBoolean(true);
        var fixed = DemoWorkspace.simulatedModel();
        fx(() -> {
            open(new MailApplication((request, config) -> {
                onUiThread.set(Platform.isFxApplicationThread()); entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return fixed.complete(request, config);
            }));
            button("Compose proposal").fire(); return null;
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        assertFalse(onUiThread.get());
        fx(() -> {
            draftBody().setText("My unsaved edit");
            return null;
        });
        release.countDown(); awaitFinished();
        assertEquals("My unsaved edit", fx(() -> draftBody().getText()));
        assertTrue(fx(() -> statusText().contains("Response discarded")));
    }

    private void select(int index) {
        ((ListView<?>)scene.lookup("#inbox")).getSelectionModel().select(index);
    }
    private TextArea draftBody() { return (TextArea)scene.lookup("#draft-body"); }
    private TextField draftSubject() { return (TextField)scene.lookup("#draft-subject"); }
    private String statusText() { return ((Label)scene.lookup("#status")).getText(); }
    private String provenanceText() { return ((Label)scene.lookup("#draft-provenance")).getText(); }

    @Test void navigationRestoresEditsAndOriginalGenerationMetadata() throws Exception {
        fx(() -> { open(new MailApplication()); button("Compose proposal").fire(); return null; });
        awaitFinished();
        fx(() -> {
            assertFalse(provenanceText().contains("Manually edited"));
            draftSubject().setText("My subject A");
            draftBody().setText("My edited proposal A");
            assertTrue(provenanceText().contains("original generation only"));
            select(1);
            assertEquals("", draftBody().getText());
            draftSubject().setText("My subject B");
            draftBody().setText("My edited proposal B");
            select(0);
            assertEquals("My subject A", draftSubject().getText());
            assertEquals("My edited proposal A", draftBody().getText());
            String metadata = provenanceText();
            assertTrue(metadata.contains("original generation only"));
            assertTrue(metadata.contains("demo-evidence-001"));
            select(1);
            assertEquals("My subject B", draftSubject().getText());
            assertEquals("My edited proposal B", draftBody().getText());
            assertEquals("", provenanceText());
            return null;
        });
    }

    @Test void delayedCancelledResultCannotAppearUnderAnotherEmail() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), returned = new CountDownLatch(1);
        var fixed = DemoWorkspace.simulatedModel();
        try {
            fx(() -> {
                open(new MailApplication((request, config) -> {
                    entered.countDown();
                    // Deliberately emulate a provider that returns even after cancellation.
                    boolean interrupted = false;
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    while (release.getCount() != 0 && System.nanoTime() < deadline) {
                        try { release.await(100, TimeUnit.MILLISECONDS); }
                        catch (InterruptedException e) { interrupted = true; }
                    }
                    var result = fixed.complete(request, config);
                    if (interrupted) Thread.currentThread().interrupt();
                    returned.countDown();
                    return result;
                }));
                draftBody().setText("Saved A");
                button("Compose proposal").fire();
                return null;
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            fx(() -> { select(1); draftBody().setText("Saved B"); return null; });
            release.countDown();
            assertTrue(returned.await(3, TimeUnit.SECONDS));
            fx(() -> {
                assertEquals("Saved B", draftBody().getText());
                select(0);
                assertEquals("Saved A", draftBody().getText());
                return null;
            });
        } finally { release.countDown(); }
    }

    @Test void realEngineSelectionUsesConfiguredHttpAndDisplaysFailuresWithoutDemoFallback() throws Exception {
        AtomicInteger httpStatus = new AtomicInteger(200);
        AtomicBoolean malformed = new AtomicBoolean(false);
        AtomicReference<JsonObject> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Gson json = new Gson();
        server.createContext("/api/chat", exchange -> {
            try {
                received.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                String draft = json.toJson(Map.of("subject", "Controlled subject", "body", "Controlled local HTTP reply.",
                        "language", "English", "usedEvidenceIds", java.util.List.of("demo-evidence-001"), "commitments", java.util.List.of()));
                byte[] bytes = (malformed.get() ? "not JSON" : httpStatus.get() == 200
                        ? json.toJson(Map.of("done", true, "message", Map.of("content", draft))) : "unavailable")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(httpStatus.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            fx(() -> {
                open(new MailApplication((r, c) -> { throw new AssertionError("Demo must not be called"); }));
                ((TextField)scene.lookup("#llm-endpoint")).setText("http://127.0.0.1:" + server.getAddress().getPort());
                ((TextField)scene.lookup("#llm-model")).setText("controlled-test-model");
                ((CheckBox)scene.lookup("#real-engine")).setSelected(true);
                button("Compose proposal").fire();
                return null;
            });
            awaitFinished();
            assertEquals("controlled-test-model", received.get().get("model").getAsString());
            fx(() -> {
                assertEquals("Controlled local HTTP reply.", draftBody().getText());
                assertTrue(statusText().contains("Local model proposal"));
                httpStatus.set(503);
                button("Compose proposal").fire();
                return null;
            });
            awaitFinished();
            fx(() -> {
                assertTrue(statusText().contains("HTTP 503"));
                assertEquals("Controlled local HTTP reply.", draftBody().getText());
                httpStatus.set(200);
                malformed.set(true);
                button("Compose proposal").fire();
                return null;
            });
            awaitFinished();
            fx(() -> {
                assertTrue(statusText().contains("Malformed local engine response"));
                assertEquals("Controlled local HTTP reply.", draftBody().getText());
                return null;
            });
        } finally { server.stop(0); }
    }
}
