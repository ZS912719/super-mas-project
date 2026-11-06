package org.supermas.context;

import java.util.Locale;
import java.util.Optional;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.BlackboardSnapshot;

/** Whole-word matching of trusted keywords against untrusted text. */
final class TopicText {
    private TopicText() { }

    /** Lower case, with every run of other characters as one space, and a space at each end. */
    static String normalize(String text) {
        return " " + String.join(" ", (text == null ? "" : text).toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+")).trim() + " ";
    }

    static boolean contains(String normalizedText, String keyword) {
        String needle = normalize(keyword);
        return needle.length() > 2 && normalizedText.contains(needle);
    }

    /** The email's new text from the analysis, or its body if the analysis has none. */
    static String currentText(BlackboardSnapshot state) {
        return Optional.ofNullable(state.analysis().metadata().get(MetadataAgent.KEY_CURRENT_TEXT))
                .orElse(state.email().body() == null ? "" : state.email().body());
    }
}
