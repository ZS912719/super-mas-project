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
 * <p>Besides the usual cues (a leading {@code >}, an attribution line, the {@code "-- "} delimiter,
 * sign-offs) it handles Lotus Notes and Outlook forms common in corporate mail, such as a
 * "Forwarded by" banner or a line ending in a timestamp. Rule-based stripping cannot be perfect:
 * see docs/reports/step-1.3b.md for what these rules get wrong. The signature cut and the
 * trailing-block heuristic are flags so that their effect can be measured.
 */
public final class QuoteSignatureStripper {
    private static final Pattern ATTRIBUTION = Pattern.compile(
            ".*(wrote|a écrit)\\s*:\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_HEADER = Pattern.compile(
            "^(from|de|sent|envoyé|to|à|subject|objet)\\s*:\\s.*", Pattern.CASE_INSENSITIVE);
    /** Lotus Notes: "Jane Doe on 07/27/2000 05:51:31 PM", or the date alone below a name line. */
    private static final Pattern ENDS_IN_TIMESTAMP = Pattern.compile(
            ".*\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+\\d{1,2}:\\d{2}(:\\d{2})?\\s*(am|pm)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ONLY_TIMESTAMP = Pattern.compile(
            "^\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+\\d{1,2}:\\d{2}(:\\d{2})?\\s*(am|pm)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORWARD_BANNER = Pattern.compile(
            "^-{3,}\\s*forwarded by\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTACHMENT_MARKER = Pattern.compile(
            "^-\\s+\\S.*\\.[a-z0-9]{2,5}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RULE_LINE = Pattern.compile("^[*=_]{5,}\\s*$|^(/\\*\\*)+/?$");
    private static final Pattern MACHINE_FOOTER = Pattern.compile(
            "sent from my blackberry|^log messages:|unsubscribe|this is a post-only mailing",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POSTSCRIPT = Pattern.compile("^p\\.?s\\.?\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTACT_DETAIL = Pattern.compile(
            "[\\w.+-]+@[\\w.-]+\\.\\w+|https?://|www\\.|\\(?\\d{3}\\)?[ .-]\\d{3}[ .-]\\d{4}|\\b(tel|fax|phone|mobile)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_END = Pattern.compile(".*[.!?:]$");
    private static final Set<String> SIGN_OFFS = Set.of(
            "best regards", "regards", "best", "best-", "kind regards", "warm regards", "thanks",
            "thank you", "many thanks", "thanks again", "thanks for your help", "thanks a lot", "cheers",
            "sincerely", "yours sincerely", "best wishes", "all the best", "take it easy",
            "cordialement", "bien à vous", "salutations", "merci", "bonne journée",
            "à bientôt", "sincèrement", "bien cordialement");
    private static final int MAX_SIGNATURE_LINES = 8;

    private final boolean stripSignature;
    private final boolean trimTrailingBlock;

    public QuoteSignatureStripper() {
        this(true, true);
    }

    public QuoteSignatureStripper(boolean stripSignature, boolean trimTrailingBlock) {
        this.stripSignature = stripSignature;
        this.trimTrailingBlock = trimTrailingBlock;
    }

    public String currentText(String body) {
        if (body == null) {
            return "";
        }
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> kept = new ArrayList<>();
        boolean inSignature = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (isQuoteBoundary(trimmed)) {
                if (ONLY_TIMESTAMP.matcher(trimmed).matches()) {
                    dropLastNonBlankLine(kept); // The name line belongs to the attribution.
                }
                break;
            }
            if (stripSignature && (isSignOff(trimmed) || isSignatureDelimiter(trimmed))) {
                inSignature = true; // Text after a sign-off is dropped, except a postscript.
                continue;
            }
            if (inSignature && !POSTSCRIPT.matcher(trimmed).matches()) {
                continue;
            }
            inSignature = false;
            kept.add(lines[i]);
        }
        trimTrailingBlanks(kept);
        if (trimTrailingBlock) {
            dropTrailingBlocks(kept);
        }
        return String.join("\n", kept).strip();
    }

    private static boolean isQuoteBoundary(String trimmed) {
        if (trimmed.startsWith(">") || trimmed.startsWith("____") || RULE_LINE.matcher(trimmed).matches()) {
            return true;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("-----original message-----")
                || lower.contains("forwarded message")
                || lower.contains("message transféré")) {
            return true;
        }
        return FORWARD_BANNER.matcher(trimmed).matches()
                || ENDS_IN_TIMESTAMP.matcher(trimmed).matches()
                || ATTACHMENT_MARKER.matcher(trimmed).matches()
                || MACHINE_FOOTER.matcher(trimmed).find()
                || ATTRIBUTION.matcher(trimmed).matches()
                || QUOTED_HEADER.matcher(trimmed).matches();
    }

    /** The RFC 3676 signature delimiter: a line of exactly two dashes (often "-- " with a space). */
    private static boolean isSignatureDelimiter(String trimmed) {
        return trimmed.equals("--");
    }

    private static boolean isSignOff(String trimmed) {
        String normalized = trimmed.toLowerCase(Locale.ROOT).replaceAll("[,.!:;]+$", "").strip();
        return SIGN_OFFS.contains(normalized);
    }

    /**
     * Drops final blocks that look like a signature: one short line without sentence punctuation
     * (usually a name), or a block of short lines with contact details. The last remaining block
     * is always kept, because a message may consist of only "FYI" or a link.
     */
    private static void dropTrailingBlocks(List<String> lines) {
        while (true) {
            int start = lines.size();
            while (start > 0 && !lines.get(start - 1).isBlank()) {
                start--;
            }
            if (start == 0 || !looksLikeSignature(lines.subList(start, lines.size()))) {
                return;
            }
            lines.subList(start, lines.size()).clear();
            trimTrailingBlanks(lines);
        }
    }

    private static boolean looksLikeSignature(List<String> block) {
        if (block.size() > MAX_SIGNATURE_LINES) {
            return false;
        }
        if (block.size() == 1) {
            String line = block.get(0).strip();
            return line.split("\\s+").length <= 4 && !SENTENCE_END.matcher(line).matches()
                    && !line.contains("://");
        }
        boolean shortLines = block.stream().allMatch(line -> line.strip().length() <= 60);
        return shortLines && block.stream().anyMatch(line -> CONTACT_DETAIL.matcher(line).find());
    }

    private static void dropLastNonBlankLine(List<String> lines) {
        trimTrailingBlanks(lines);
        if (!lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
    }

    private static void trimTrailingBlanks(List<String> lines) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
    }
}
