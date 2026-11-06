package org.supermas.metadata;

import java.nio.file.Path;
import java.util.List;

/** Step 1.5: prints what the Java route extracts, for comparison with the Python library. */
public final class GlinerSpike {
    private GlinerSpike() { }

    public static void main(String[] args) throws Exception {
        String text = "Dear Prof. Martin,\n\nI have been ill this week. Could I have an extension for the"
                + " CS204 project? The deadline is Friday 6 November. My student number is 20231187.";
        List<String> labels = List.of("person", "course", "date", "deadline", "identifier", "organization");
        long start = System.nanoTime();
        try (GlinerEntityModel model = new GlinerEntityModel(Path.of(args[0]), args[1], 0.5)) {
            System.out.printf("load: %.1fs%n", (System.nanoTime() - start) / 1e9);
            for (int i = 0; i < 3; i++) {
                start = System.nanoTime();
                List<GlinerEntityModel.Span> spans = model.predict(text, labels);
                System.out.printf("predict: %.2fs%n", (System.nanoTime() - start) / 1e9);
                if (i == 0) {
                    spans.forEach(s -> System.out.printf("  %-12s %s (%.2f)%n", s.label(), s.text(), s.score()));
                }
            }
        }
    }
}
