package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Runs only where the Git-ignored export and its parity file exist; see docs/reports/step-1.8.md. */
@EnabledIf("available")
class OnnxSpeechActClassifierTest {
    private static final Path DIRECTORY = Path.of("tmp/models/speech-act");
    private static final Path DEV = Path.of("benchmark/data/enron/labeling/dev");

    static boolean available() {
        return Files.exists(DIRECTORY.resolve("parity.json"));
    }

    /** The whole chain, tokenizer included, must reproduce Python's output on real email text. */
    @Test
    void reproducesPythonOnEveryDevEmail() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode parity = json.readTree(DIRECTORY.resolve("parity.json").toFile());
        double worst = 0;
        try (OnnxSpeechActClassifier classifier = new OnnxSpeechActClassifier(DIRECTORY)) {
            for (JsonNode row : parity) {
                String stem = row.get("stem").asText();
                Path gold = DEV.resolve(stem + ".gold.json");
                Path label = Files.exists(gold) ? gold : DEV.resolve(stem + ".gold.draft.json");
                String text = json.readTree(label.toFile()).get("currentText").asText();
                List<String> expected = new ArrayList<>();
                row.get("acts").forEach(act -> expected.add(act.asText()));
                assertEquals(expected, classifier.classify(text).stream().map(SpeechAct::act).toList(), stem);
                float[] embedding = classifier.embed(text);
                for (int i = 0; i < row.get("embedding").size(); i++) {
                    worst = Math.max(worst, Math.abs(embedding[i] - row.get("embedding").get(i).asDouble()));
                }
            }
        }
        assertTrue(worst < 1e-4, "largest embedding difference " + worst);
    }

    @Test
    void blankTextIsNotSentToTheModel() throws Exception {
        try (OnnxSpeechActClassifier classifier = new OnnxSpeechActClassifier(DIRECTORY)) {
            assertEquals(List.of(), classifier.classify("  \n "));
            assertEquals(List.of(), classifier.classify(null));
        }
    }
}
