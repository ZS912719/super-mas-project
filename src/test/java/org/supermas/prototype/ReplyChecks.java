package org.supermas.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Automatic checks of one reply against its frozen expectations ({@code <email>.reply.json}, schema
 * {@code reply-gold/v1}). The checks are keyword-based, so they approximate a human rubric: they find a missing
 * fact or a forbidden phrase reliably, but cannot judge tone or whether a sentence is true in context. Matching
 * ignores case, accents and apostrophe variants.
 */
final class ReplyChecks {
    static final String SCHEMA = "reply-gold/v1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[^\\[\\]\\n]{1,40}\\]");
    /** Phrases of the staff-only marking guide (`teaching-guide`), in both languages. */
    static final List<String> INTERNAL = List.of("fifteen working days", "15 working days", "exam board", "internal:",
            "quinze jours ouvres", "jury d'examen");
    private static final List<String> ASK_CUES = List.of("please", "could you", "can you", "would you", "send", "provide",
            "attach", "share", "let me know", "merci de", "pourriez", "pouvez", "veuillez", "envoyer", "fournir",
            "joindre", "transmettre", "faire parvenir");
    private static final Set<String> ENGLISH = Set.of("the", "and", "to", "you", "your", "is", "for", "of", "please", "we",
            "i", "will", "be", "with");
    private static final Set<String> FRENCH = Set.of("le", "la", "les", "et", "vous", "votre", "est", "pour", "de", "merci",
            "nous", "je", "des", "une", "du", "avec");

    private ReplyChecks() { }

    /** One expectation: satisfied when any of the terms occurs. */
    record Term(String id, List<String> any) { }

    record Gold(String language, boolean replyExpected, Optional<List<String>> greet, List<String> notGreet,
                List<Term> mustMention, List<Term> mustAsk, List<Term> mustNotContain, boolean allowInternal, String note) { }

    /**
     * @param violations ids of broken prohibitions: forbidden terms, {@code placeholder}, {@code internal},
     *                   {@code wrong-greeting}, {@code wrong-language}
     */
    record Score(boolean greetingOk, boolean languageOk, List<String> mentioned, List<String> notMentioned,
                 List<String> asked, List<String> notAsked, List<String> violations) {
        boolean clean() {
            return violations.isEmpty();
        }
    }

    static Gold load(Path file) throws IOException {
        JsonNode node = JSON.readTree(file.toFile());
        if (!SCHEMA.equals(node.path("schema").asText())) {
            throw new IOException(file + " is not a " + SCHEMA + " file");
        }
        Optional<List<String>> greet = node.path("greet").isArray() ? Optional.of(strings(node.path("greet"))) : Optional.empty();
        return new Gold(node.path("language").asText(), node.path("replyExpected").asBoolean(), greet,
                strings(node.path("notGreet")), terms(node.path("mustMention")), terms(node.path("mustAsk")),
                terms(node.path("mustNotContain")), node.path("allowInternal").asBoolean(), node.path("note").asText());
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static List<Term> terms(JsonNode array) {
        List<Term> terms = new ArrayList<>();
        array.forEach(term -> terms.add(new Term(term.path("id").asText(), strings(term.path("any")))));
        return terms;
    }

    /** Lower case, no accents, one kind of apostrophe, single spaces. */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'').replaceAll("\\s+", " ").strip();
    }

    private static boolean containsAny(String normalizedText, List<String> terms) {
        return terms.stream().anyMatch(term -> normalizedText.contains(normalize(term)));
    }

    static Score score(Gold gold, String body) {
        String text = normalize(body);
        List<String> violations = new ArrayList<>();

        String greeting = body.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().map(ReplyChecks::normalize)
                .orElse("");
        boolean greetingOk = !containsAny(greeting, gold.notGreet())
                && gold.greet().map(names -> containsAny(greeting, names)).orElse(true);
        if (!greetingOk) {
            violations.add("wrong-greeting");
        }
        boolean languageOk = gold.language().equals(language(text));
        if (!languageOk) {
            violations.add("wrong-language");
        }
        List<String> mentioned = new ArrayList<>();
        List<String> notMentioned = new ArrayList<>();
        for (Term term : gold.mustMention()) {
            (containsAny(text, term.any()) ? mentioned : notMentioned).add(term.id());
        }
        List<String> sentences = body.lines().flatMap(line -> Pattern.compile("(?<=[.!?])\\s+").splitAsStream(line))
                .map(ReplyChecks::normalize).filter(sentence -> !sentence.isEmpty()).toList();
        List<String> asked = new ArrayList<>();
        List<String> notAsked = new ArrayList<>();
        for (Term term : gold.mustAsk()) {
            boolean ok = sentences.stream().anyMatch(sentence -> containsAny(sentence, term.any())
                    && (sentence.contains("?") || ASK_CUES.stream().anyMatch(sentence::contains)));
            (ok ? asked : notAsked).add(term.id());
        }
        for (Term term : gold.mustNotContain()) {
            if (containsAny(text, term.any())) {
                violations.add(term.id());
            }
        }
        if (PLACEHOLDER.matcher(body).find()) {
            violations.add("placeholder");
        }
        if (!gold.allowInternal() && containsAny(text, INTERNAL)) {
            violations.add("internal");
        }
        if (signed(body)) {
            violations.add("signature");
        }
        return new Score(greetingOk, languageOk, mentioned, notMentioned, asked, notAsked, violations);
    }

    private static final Pattern CLOSING = Pattern.compile("^(?:(?:best|kind|warm|warmest)\\s+)?(?:regards|wishes)[,.!]?$"
            + "|^(?:best|sincerely|thanks|thank you|cordialement|bien a vous|salutations|merci|respectueusement)[,.!]?$");

    /**
     * True if a name follows the closing: the last line has at most four words and the line before it is a
     * closing ("Best regards,"). The common rules say nothing follows the closing (revision 2 of the checks).
     */
    static boolean signed(String body) {
        List<String> lines = body.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (lines.size() < 2) {
            return false;
        }
        String last = lines.get(lines.size() - 1);
        return CLOSING.matcher(normalize(lines.get(lines.size() - 2))).matches()
                && !CLOSING.matcher(normalize(last)).matches() && last.split("\\s+").length <= 4;
    }

    /** "en", "fr", or "unknown" when the stop words do not decide. */
    static String language(String normalizedText) {
        int english = 0;
        int french = 0;
        for (String word : normalizedText.split("[^a-z']+")) {
            if (ENGLISH.contains(word)) {
                english++;
            }
            if (FRENCH.contains(word)) {
                french++;
            }
        }
        return english == french ? "unknown" : english > french ? "en" : "fr";
    }
}
