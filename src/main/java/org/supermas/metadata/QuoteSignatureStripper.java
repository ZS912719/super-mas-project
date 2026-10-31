package org.supermas.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes quoted replies, forwarded content and signatures from an email body, leaving the new text
 * the sender wrote (the schema's {@code currentText}). Rule-based, for English and French.
 *
 * <p>These are well-known cues (a leading {@code >}, an attribution line, the {@code "-- "}
 * delimiter, common sign-offs) plus one heuristic that drops a short trailing name line. Rule-based
 * stripping cannot be perfect; the real evaluation is the Quagga zone data (deferred). Both the
 * signature cut and the trailing-name heuristic are flags so their effect can be measured.
 */
public final class QuoteSignatureStripper {
    private static final Pattern ATTRIBUTION = Pattern.compile(
            ".*(wrote|a écrit)\\s*:\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_HEADER = Pattern.compile(
            "^(from|de|sent|envoyé|to|à|subject|objet)\\s*:\\s.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_END = Pattern.compile(".*[.!?:]$");
    private static final Set<String> SIGN_OFFS = Set.of(
            "best regards", "regards", "best", "kind regards", "warm regards", "thanks",
            "thank you", "many thanks", "cheers", "sincerely", "yours sincerely", "best wishes",
            "cordialement", "bien à vous", "salutations", "merci", "bonne journée",
            "à bientôt", "sincèrement", "bien cordialement");

    private final boolean stripSignature;
    private final boolean trimTrailingName;

    public QuoteSignatureStripper() {
        this(true, true);
    }

    public QuoteSignatureStripper(boolean stripSignature, boolean trimTrailingName) {
        this.stripSignature = stripSignature;
        this.trimTrailingName = trimTrailingName;
    }

    public String currentText(String body) {
        if (body == null) {
            return "";
        }
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int cut = lines.length;
        for (int i = 0; i < lines.length; i++) {
            if (isQuoteBoundary(lines[i])
                    || (stripSignature && (isSignOff(lines[i]) || isSignatureDelimiter(lines[i])))) {
                cut = i;
                break;
            }
        }
        List<String> kept = new ArrayList<>(List.of(lines).subList(0, cut));
        trimTrailingBlanks(kept);
        if (trimTrailingName) {
            dropTrailingNameLine(kept);
        }
        return String.join("\n", kept).strip();
    }

    private static boolean isQuoteBoundary(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith(">") || trimmed.startsWith("____")) {
            return true;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("-----original message-----")
                || lower.contains("forwarded message")
                || lower.contains("message transféré")) {
            return true;
        }
        return ATTRIBUTION.matcher(trimmed).matches() || QUOTED_HEADER.matcher(trimmed).matches();
    }

    /** The RFC 3676 signature delimiter: a line of exactly two dashes (often "-- " with a space). */
    private static boolean isSignatureDelimiter(String line) {
        return line.strip().equals("--");
    }

    private static boolean isSignOff(String line) {
        String normalized = line.strip().toLowerCase(Locale.ROOT).replaceAll("[,.!:;]+$", "").strip();
        return SIGN_OFFS.contains(normalized);
    }

    /**
     * Drops a final block that is a single short line without sentence punctuation, which is almost
     * always a name or team signature (for example "Sarah" or "Example University Library"). This
     * can over-trim a genuinely short last line, which is why it is a flag.
     */
    private static void dropTrailingNameLine(List<String> lines) {
        int last = lines.size() - 1;
        if (last < 0) {
            return;
        }
        // A blank line (or the start) must precede the candidate, so it is its own block.
        boolean ownBlock = last == 0 || lines.get(last - 1).isBlank();
        String candidate = lines.get(last).strip();
        boolean looksLikeName = !candidate.isEmpty() && candidate.split("\\s+").length <= 4
                && !SENTENCE_END.matcher(candidate).matches();
        if (ownBlock && looksLikeName) {
            lines.remove(last);
            trimTrailingBlanks(lines);
        }
    }

    private static void trimTrailingBlanks(List<String> lines) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
    }
}
