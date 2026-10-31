package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.supermas.benchmark.Dataset;
import org.supermas.benchmark.Scorer;

class MetadataPipelineTest {
    private final MimeEmailParser parser = new MimeEmailParser();
    private final QuoteSignatureStripper stripper = new QuoteSignatureStripper();
    private final LanguageDetector detector = new LanguageDetector();

    private Dataset dataset() throws Exception {
        return Dataset.load(Path.of("benchmark/data/synthetic-dev"));
    }

    @Test
    void stripsToTheExpectedCurrentTextOnAllSyntheticEmails() throws Exception {
        int matches = 0;
        StringBuilder misses = new StringBuilder();
        for (Dataset.Case testCase : dataset().cases()) {
            String body = parser.parse(testCase.rawEmail(), "p").body();
            String got = Scorer.normalize(stripper.currentText(body));
            String want = Scorer.normalize(testCase.gold().currentText());
            if (got.equals(want)) {
                matches++;
            } else {
                misses.append("\n  ").append(testCase.id());
            }
        }
        assertEquals(dataset().cases().size(), matches, "currentText mismatches:" + misses);
    }

    @Test
    void detectsLanguageOnAllSyntheticEmails() throws Exception {
        for (Dataset.Case testCase : dataset().cases()) {
            assertEquals(testCase.gold().language(), detector.detect(testCase.gold().currentText()),
                    testCase.id());
        }
    }

    @Test
    void signatureStrippingCanBeDisabled() {
        String body = "Hello,\n\nThe meeting is at noon.\n\nBest regards,\nAlex";
        assertEquals("Hello,\n\nThe meeting is at noon.",
                new QuoteSignatureStripper(true, true).currentText(body));
        // With signature stripping off, the sign-off and name stay (the trailing-name heuristic
        // still drops the final bare name, so turn both off to keep everything).
        assertEquals(body, new QuoteSignatureStripper(false, false).currentText(body));
    }
}
