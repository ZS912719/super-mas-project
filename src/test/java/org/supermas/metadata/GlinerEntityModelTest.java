package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Runs only where the Git-ignored model file exists; see docs/reports/step-1.5.md to fetch it. */
@EnabledIf("modelAvailable")
class GlinerEntityModelTest {
    private static final Path DIRECTORY = Path.of("tmp/models/gliner-multi");
    private static final String FILE = "model_fp16.onnx";
    private static final List<String> LABELS =
            List.of("person", "course", "date", "deadline", "identifier", "organization");

    static boolean modelAvailable() {
        return Files.exists(DIRECTORY.resolve(FILE));
    }

    /** These spans were produced by the Python gliner library on the same text and labels. */
    @Test
    void reproducesThePythonLibrarysSpans() throws Exception {
        String text = "Dear Prof. Martin,\n\nI have been ill this week. Could I have an extension for the"
                + " CS204 project? The deadline is Friday 6 November. My student number is 20231187.";
        try (GlinerEntityModel model = new GlinerEntityModel(DIRECTORY, FILE, 0.5)) {
            List<String> found = model.predict(text, LABELS).stream()
                    .map(span -> span.label() + ":" + span.text()).toList();
            assertEquals(List.of("person:Prof. Martin", "organization:CS204 project",
                    "date:Friday 6 November", "identifier:student number", "identifier:20231187"), found);
        }
    }

    /** A chunk boundary must not lose text: an entity after it is still found at the right offset. */
    @Test
    void longTextIsChunkedAndOffsetsPointIntoTheOriginalText() throws Exception {
        String text = "word ".repeat(700) + "Please contact Sarah Klein about this.";
        try (GlinerEntityModel model = new GlinerEntityModel(DIRECTORY, FILE, 0.5)) {
            List<GlinerEntityModel.Span> spans = model.predict(text, LABELS);
            assertFalse(spans.isEmpty());
            for (GlinerEntityModel.Span span : spans) {
                assertEquals(span.text(), text.substring(span.start(), span.end()));
            }
        }
    }
}
