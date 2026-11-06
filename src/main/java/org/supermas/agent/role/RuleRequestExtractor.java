package org.supermas.agent.role;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sentences that ask for something, by punctuation and fixed English and French phrases. Sentences are copied
 * as written, never rephrased, and quoted lines are skipped.
 */
public final class RuleRequestExtractor implements RequestExtractor {
    static final int MAX_REQUESTS = 5;
    static final int MAX_LENGTH = 300;
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.?!])\\s+|\\n+");
    private static final Pattern ASKING = Pattern.compile("\\b(?:please|kindly|could you|can you|would you|will you"
            + "|could i|can i|may i|i would like|i'd like|let me know|i need|is it possible|pourriez-vous|pouvez-vous"
            + "|merci de|je voudrais|j'aimerais|est-ce que|serait-il possible|auriez-vous)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public Requests extract(String currentText, String language) {
        List<String> requests = new ArrayList<>();
        for (String sentence : SENTENCE_END.split(currentText == null ? "" : currentText)) {
            String trimmed = sentence.strip();
            if (trimmed.isEmpty() || trimmed.startsWith(">")) {
                continue;
            }
            if (trimmed.endsWith("?") || ASKING.matcher(trimmed).find()) {
                requests.add(trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed);
            }
            if (requests.size() == MAX_REQUESTS) {
                break;
            }
        }
        return new Requests(requests, "rules");
    }
}
