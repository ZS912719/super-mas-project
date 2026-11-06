package org.supermas.benchmark;

import java.util.List;

/** Benchmark view of one email's metadata, used for both gold labels and predictions. */
public record Extraction(String sender, List<String> recipients, String date, String threadId,
                         String currentText, List<Entity> entities, List<String> speechActs,
                         String roleHint, String language) {
    public record Entity(String type, String text) { }

    public static Extraction empty() {
        return new Extraction(null, List.of(), null, null, null, List.of(), List.of(), null, null);
    }
}
