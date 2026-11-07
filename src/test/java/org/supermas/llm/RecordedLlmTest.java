package org.supermas.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.ModelConfig;
import org.supermas.model.Domain.ModelRequest;
import org.supermas.model.Domain.ModelResponse;

/** Replies recorded once can be replayed without a model; anything else is an explicit miss. */
class RecordedLlmTest {
    private static final ModelRequest REQUEST = new ModelRequest("composer", "RULES", "<<EMAIL>> Could I have an extension?", 10_000);
    private static final ModelConfig MODEL = new ModelConfig("model-a", URI.create("http://127.0.0.1:1"), Duration.ofSeconds(1), 1000);

    @Test
    void aRecordedReplyIsReplayedWithoutTheModel(@TempDir Path folder) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LocalLlm model = (request, config) -> {
            calls.incrementAndGet();
            return new ModelResponse(Optional.of("Dear Sarah, yes."), Optional.empty());
        };
        assertEquals("Dear Sarah, yes.", RecordedLlm.recording(model, folder).complete(REQUEST, MODEL).output().orElseThrow());
        assertEquals(1, calls.get());

        ModelResponse replayed = RecordedLlm.replay(folder).complete(REQUEST, MODEL);
        assertEquals("Dear Sarah, yes.", replayed.output().orElseThrow());
        assertTrue(replayed.failure().isEmpty());
        assertEquals(1, calls.get(), "the replay did not call the model");
        try (Stream<Path> files = Files.list(folder)) {
            String recorded = Files.readString(files.findFirst().orElseThrow());
            assertTrue(recorded.contains(RecordedLlm.SCHEMA));
            assertFalse(recorded.contains("RULES"), "the prompt is not stored");
        }
    }

    @Test
    void anyChangeToThePromptTheDataOrTheModelIsAnExplicitMiss(@TempDir Path folder) {
        RecordedLlm.recording((request, config) -> new ModelResponse(Optional.of("Reply"), Optional.empty()), folder)
                .complete(REQUEST, MODEL);
        RecordedLlm replay = RecordedLlm.replay(folder);
        ModelRequest otherData = new ModelRequest("composer", "RULES", "<<EMAIL>> Something else", 10_000);
        ModelRequest otherRules = new ModelRequest("composer", "OTHER RULES", REQUEST.untrustedData(), 10_000);
        ModelConfig otherModel = new ModelConfig("model-b", MODEL.endpoint(), MODEL.timeout(), 1000);
        for (ModelResponse miss : new ModelResponse[] {replay.complete(otherData, MODEL), replay.complete(otherRules, MODEL),
                replay.complete(REQUEST, otherModel)}) {
            assertEquals(FailureKind.CONNECTION, miss.failure().orElseThrow().kind());
            assertTrue(miss.output().isEmpty());
        }
    }

    @Test
    void failuresAreNotRecordedAndTheBudgetStillHolds(@TempDir Path folder) throws Exception {
        RecordedLlm.recording((request, config) -> new ModelResponse(Optional.empty(),
                Optional.of(new Failure(FailureKind.TIMEOUT, "slow", true))), folder).complete(REQUEST, MODEL);
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(0, files.count());
        }
        ModelRequest tooLong = new ModelRequest("composer", "x".repeat(60), "y".repeat(60), 100);
        assertEquals(FailureKind.BUDGET_EXHAUSTED, RecordedLlm.replay(folder).complete(tooLong, MODEL)
                .failure().orElseThrow().kind());
    }
}
