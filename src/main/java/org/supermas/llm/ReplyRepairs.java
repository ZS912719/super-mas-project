package org.supermas.llm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two deterministic repairs of a model's reply, from the failures of the 8 B model in the main runs (benchmark
 * protocol B59, B61): a reply that starts by copying the prompt's header blocks, and a greeting addressed to the
 * recipient of the incoming email instead of its sender. Both work only on what the request itself contains.
 */
final class ReplyRepairs {
    private static final Pattern GREETING = Pattern.compile(
            "(?i)^(dear|hello|hi|hey|bonjour|bonsoir|cher|ch[eè]re|madame|monsieur)\\b[ \\t]*([^,!:\\n]{0,60}?)[ \\t]*[,!:]?[ \\t]*$");
    private static final Set<String> TITLES = Set.of("prof", "professor", "dr", "mr", "mrs", "ms", "miss", "madame",
            "monsieur", "mme", "m", "mlle", "sir", "madam", "dear");
    private static final Set<String> FRENCH = Set.of("bonjour", "bonsoir", "cher", "chère", "chere", "madame", "monsieur");
    private static final Pattern FROM = Pattern.compile("(?m)^From \\(the sender, who receives the reply\\): (.*)$");
    private static final Pattern TO = Pattern.compile("(?m)^To \\(the recipient, on whose behalf the reply is written\\): (.*)$");
    private static final Pattern NAME_AND_ADDRESS = Pattern.compile("^\\s*\"?([^\"<]*?)\"?\\s*<([^<>]+)>\\s*$");
    private static final int SHINGLE = 8;

    private ReplyRepairs() { }

    /** A greeting fixed: the new body and a note for the rationale. */
    record Fixed(String body, String note) { }

    /**
     * Removes header blocks the model copied from its input at the start of the reply ({@code <<EMAIL ADDRESSES>>},
     * its From and To lines, {@code <<EMAIL SUBJECT>>} and the subject line). Empty when the reply does not start so,
     * when nothing is left, when another block label remains, or when the rest mostly repeats the email.
     */
    static Optional<String> withoutEchoedHeader(String body, String emailText) {
        List<String> lines = body.lines().toList();
        int i = 0;
        boolean skipped = false;
        while (i < lines.size()) {
            String line = lines.get(i).strip();
            if (line.startsWith("<<EMAIL ADDRESSES>>") || line.startsWith("<<EMAIL SUBJECT>>")) {
                skipped = true;
                i++;
                if (line.startsWith("<<EMAIL SUBJECT>>") && i < lines.size() && !lines.get(i).strip().startsWith("<<")) {
                    i++; // the subject line
                }
            } else if (skipped && (line.isEmpty() || line.matches("(?i)^(from|to)\\b.*:.*"))) {
                i++;
            } else {
                break;
            }
        }
        if (!skipped || i >= lines.size()) {
            return Optional.empty();
        }
        String rest = String.join("\n", lines.subList(i, lines.size())).strip();
        if (rest.isEmpty() || LlmGenerationService.PROMPT_BLOCK.matcher(rest).find() || repeats(rest, emailText)) {
            return Optional.empty();
        }
        return Optional.of(rest);
    }

    /** True when most 8-word passages of {@code text} are in {@code email}: the email copied, not a reply. */
    static boolean repeats(String text, String email) {
        Set<String> mine = shingles(text);
        if (mine.isEmpty()) {
            return false;
        }
        Set<String> theirs = shingles(email);
        long copied = mine.stream().filter(theirs::contains).count();
        return copied * 2 >= mine.size();
    }

    /**
     * Replaces the name in the greeting when it is not the sender's but the recipient's or the one the email itself
     * greets (the model wrote to the person the email was addressed to). The sender's display name takes its place,
     * or a plain greeting when the sender has none. Empty when nothing needs fixing or the case is unclear.
     */
    static Optional<Fixed> withSendersGreeting(String body, String untrustedData, String emailText) {
        List<String> lines = body.lines().toList();
        int first = 0;
        while (first < lines.size() && lines.get(first).isBlank()) {
            first++;
        }
        if (first >= lines.size()) {
            return Optional.empty();
        }
        Matcher greeting = GREETING.matcher(lines.get(first).strip());
        if (!greeting.matches()) {
            return Optional.empty();
        }
        String word = greeting.group(1);
        String named = greeting.group(2).strip();
        Set<String> greeted = tokens(named);
        if (greeted.isEmpty()) {
            return Optional.empty();
        }
        String from = field(FROM, untrustedData);
        String to = field(TO, untrustedData);
        String senderName = displayName(from);
        Set<String> sender = new HashSet<>(tokens(senderName));
        sender.addAll(tokens(localPart(address(from))));
        if (greeted.stream().anyMatch(sender::contains)) {
            return Optional.empty();
        }
        Set<String> addressee = new HashSet<>();
        for (String recipient : to.split(",")) {
            addressee.addAll(tokens(displayName(recipient)));
            addressee.addAll(tokens(localPart(address(recipient))));
        }
        String salutation = emailText == null ? "" : emailText.lines().map(String::strip).filter(l -> !l.isEmpty())
                .findFirst().orElse("");
        Matcher theirs = GREETING.matcher(salutation);
        if (theirs.matches()) {
            addressee.addAll(tokens(theirs.group(2)));
        }
        if (greeted.stream().noneMatch(addressee::contains)) {
            return Optional.empty();
        }
        String lower = word.toLowerCase(Locale.ROOT);
        // "Madame Dubois," greets with a title; with another person's name the title may be wrong, so "Bonjour".
        String opener = lower.equals("madame") || lower.equals("monsieur") ? "Bonjour" : word;
        String replacement = senderName.isBlank() ? (FRENCH.contains(lower) ? "Bonjour," : "Hello,")
                : opener + " " + senderName.strip() + ",";
        List<String> fixed = new java.util.ArrayList<>(lines);
        fixed.set(first, replacement);
        return Optional.of(new Fixed(String.join("\n", fixed), "greeting changed from \"" + named + "\" (an addressee of the email) to the sender"));
    }

    private static String field(Pattern pattern, String data) {
        Matcher matcher = pattern.matcher(data == null ? "" : data);
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private static String displayName(String value) {
        Matcher matcher = NAME_AND_ADDRESS.matcher(value == null ? "" : value);
        if (!matcher.matches()) {
            return "";
        }
        String name = matcher.group(1).strip();
        return name.contains("@") ? "" : name;
    }

    private static String address(String value) {
        Matcher matcher = NAME_AND_ADDRESS.matcher(value == null ? "" : value);
        return (matcher.matches() ? matcher.group(2) : value == null ? "" : value).strip();
    }

    private static String localPart(String address) {
        int at = address.indexOf('@');
        return at < 0 ? "" : address.substring(0, at);
    }

    /** Lower-case name parts of at least two letters, without titles. */
    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        for (String part : DecisionPhrases.fold(text == null ? "" : text).split("[^\\p{L}]+")) {
            if (part.length() >= 2 && !TITLES.contains(part)) {
                out.add(part);
            }
        }
        return out;
    }

    private static Set<String> shingles(String text) {
        List<String> words = Arrays.stream(DecisionPhrases.fold(text == null ? "" : text).split("[^\\p{L}\\p{N}']+"))
                .filter(w -> !w.isEmpty()).toList();
        Set<String> out = new HashSet<>();
        for (int i = 0; i + SHINGLE <= words.size(); i++) {
            out.add(String.join(" ", words.subList(i, i + SHINGLE)));
        }
        return out;
    }
}
