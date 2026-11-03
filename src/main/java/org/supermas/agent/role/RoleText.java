package org.supermas.agent.role;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.supermas.context.MissingInformationAgent;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.BlackboardSnapshot;

/** Whole-word matching of trusted role keywords against untrusted email text, as for topics. */
final class RoleText {
    static final String MISSING_PREFIX = MissingInformationAgent.TEXT_PREFIX;

    private RoleText() { }

    static String currentText(BlackboardSnapshot state) {
        return Optional.ofNullable(state.analysis().metadata().get(MetadataAgent.KEY_CURRENT_TEXT))
                .orElse(state.email().body() == null ? "" : state.email().body());
    }

    static String normalize(String text) {
        return " " + String.join(" ", (text == null ? "" : text).toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+")).trim() + " ";
    }

    static long matchedKeywords(List<String> keywords, BlackboardSnapshot state) {
        String text = normalize((state.email().subject() == null ? "" : state.email().subject()) + " "
                + currentText(state));
        return keywords.stream().distinct().filter(keyword -> {
            String needle = normalize(keyword);
            return needle.length() > 2 && text.contains(needle);
        }).count();
    }
}
