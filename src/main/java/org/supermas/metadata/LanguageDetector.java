package org.supermas.metadata;

import java.util.Locale;
import java.util.Set;

/**
 * Tells English from French by counting common function words. This is a light heuristic for a
 * two-language project, not a general language identifier. It returns {@code "en"} or {@code "fr"},
 * defaulting to {@code "en"} on a tie or empty text.
 */
public final class LanguageDetector {
    private static final Set<String> ENGLISH = Set.of(
            "the", "and", "to", "of", "a", "in", "is", "you", "for", "on", "with", "this", "that",
            "your", "please", "will", "we", "i", "it", "be", "have", "can", "could", "would");
    private static final Set<String> FRENCH = Set.of(
            "le", "la", "les", "de", "des", "et", "un", "une", "vous", "pour", "je", "nous", "votre",
            "que", "à", "est", "dans", "ce", "sur", "bonjour", "cordialement", "merci", "pas",
            "avec", "au", "du", "il", "ne");

    public String detect(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }
        int english = 0;
        int french = 0;
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}à-ÿ]+")) {
            if (ENGLISH.contains(token)) {
                english++;
            }
            if (FRENCH.contains(token)) {
                french++;
            }
        }
        return french > english ? "fr" : "en";
    }
}
