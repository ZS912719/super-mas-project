package org.supermas.metadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Suggests the sender's role from what they say about themselves. The first matching role wins, and
 * the result stays empty without clear evidence. A role hint is claimed text in an untrusted email,
 * so it may help routing but must never grant permissions. Written from the schema, like the
 * speech-act rules, and English-only for now.
 */
public final class RuleRoleHintClassifier {
    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put("student", phrases("i am a student", "i'?m a student", "my student (?:number|id)",
                "my (?:thesis )?supervisor", "my professor", "my thesis", "my grade", "my enrolment", "my enrollment"));
        PATTERNS.put("teacher", phrases("i teach", "i am teaching", "i'?m teaching", "my students", "my class",
                "the course i teach"));
        PATTERNS.put("administration", phrases("registrar'?s office", "student services", "academic office",
                "on behalf of the administration"));
        PATTERNS.put("researcher", phrases("my research", "our research", "our paper", "my paper", "my lab", "our lab"));
    }

    private static Pattern phrases(String... alternatives) {
        return Pattern.compile("\\b(?:" + String.join("|", alternatives) + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    public Optional<String> classify(String currentText) {
        if (currentText == null) {
            return Optional.empty();
        }
        return PATTERNS.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(currentText).find())
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
