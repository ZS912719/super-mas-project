package org.supermas.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.supermas.model.Domain.Email;

/**
 * Decides whether the recipient needs to reply to an email (plan {@code docs/plans/reply-needed.md}), with rules that
 * give their reasons. An unsure case counts as needing a reply: a missed reply costs more than an extra draft.
 *
 * <p>Rules, in order: mail sent by the owner needs no reply; automated or bulk mail (no-reply senders, mailing-list
 * headers, "automated e-mail", "do not reply") needs none; a broadcast (a group address or many recipients) needs
 * none unless it asks a question; a question, request or proposal needs a reply; a short thank-you or acknowledgement
 * needs none; anything else without a question, request or proposal is information only and needs none.
 *
 * <p>Built on the synthetic sets and the Enron development set only; the Enron test set is kept for measuring it.
 */
public final class ReplyNeedClassifier {
    /** The decision; {@code UNSURE} is treated as {@code NEEDED} by callers. */
    public enum Verdict { NEEDED, NOT_NEEDED, UNSURE }

    public record Decision(Verdict verdict, List<String> reasons) {
        public Decision {
            reasons = List.copyOf(reasons);
        }

        /** True unless no reply is needed: an unsure decision asks for a reply. */
        public boolean replyNeeded() {
            return verdict != Verdict.NOT_NEEDED;
        }
    }

    private static final Pattern AUTOMATED_SENDER = Pattern.compile(
            "^(?:no-?reply|do-?not-?reply|donotreply|mailer-daemon|postmaster|bounces?|notifications?|notify|alerts?|"
                    + "newsletters?|news|digest|auto(?:mated)?|system|daemon)\\b.*");
    private static final Pattern AUTOMATED_TEXT = Pattern.compile(
            "\\b(?:this is an automated|this (?:is an|was an|in an) automated|automated (?:e-?mail|message|notification)|"
                    + "do not reply|please do not respond|ne pas r[eé]pondre|message automatique|to unsubscribe|"
                    + "unsubscribe from)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_ADDRESS = Pattern.compile(
            "^(?:all|all[-_.].*|everyone|everybody|.*[-_.]all|staff|students|faculty|department|team|announce.*|list|"
                    + ".*[-_.]list|tous|etudiants|personnel)@.*");
    private static final int MANY_RECIPIENTS = 10;
    private static final Pattern THANKS = Pattern.compile(
            "\\b(?:thanks?|thank you|many thanks|merci|got it|noted|sounds good|perfect|great|cheers|appreciated?|"
                    + "parfait|bien re[cç]u|c'est not[eé])\\b", Pattern.CASE_INSENSITIVE);
    private static final int SHORT_WORDS = 30;
    private static final Pattern URL = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GREETING_OR_CLOSING = Pattern.compile(
            "(?i)^(?:(?:dear|hello|hi|hey|bonjour|madame|monsieur|cher|chère)\\b.*|(?:best|kind|warm)?\\s*regards.*|"
                    + "best,?|cheers,?|cordialement.*|bien (?:a|à) vous.*|[A-Z][\\p{L}.-]*(?:\\s+[A-Z][\\p{L}.-]*){0,2},?)$");

    private final Set<String> owners;

    /** @param owners lower-case addresses of the mailbox owner; empty when not configured */
    public ReplyNeedClassifier(Set<String> owners) {
        this.owners = owners.stream().map(a -> a.trim().toLowerCase(Locale.ROOT)).filter(a -> !a.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param currentText the sender's new text, without quotes and signature
     * @param acts        the speech acts found in {@code currentText}
     */
    public Decision classify(Email email, String currentText, List<String> acts) {
        String sender = email.sender() == null ? "" : email.sender().trim().toLowerCase(Locale.ROOT);
        String text = currentText == null ? "" : currentText;
        if (!sender.isEmpty() && owners.contains(sender)) {
            return new Decision(Verdict.NOT_NEEDED, List.of("sent by the mailbox owner"));
        }
        List<String> automated = automated(sender, email.metadata(), text);
        if (!automated.isEmpty()) {
            return new Decision(Verdict.NOT_NEEDED, automated);
        }
        boolean question = URL.matcher(text).replaceAll(" ").contains("?");
        boolean asks = acts.contains("request") || acts.contains("propose");
        List<String> recipients = email.recipients() == null ? List.of() : email.recipients();
        boolean group = recipients.stream().anyMatch(r -> r != null && GROUP_ADDRESS.matcher(r.trim().toLowerCase(Locale.ROOT)).matches());
        if (group || recipients.size() >= MANY_RECIPIENTS) {
            String why = group ? "sent to a group address" : "sent to " + recipients.size() + " recipients";
            return question ? new Decision(Verdict.UNSURE, List.of("broadcast (" + why + ") that asks a question"))
                    : new Decision(Verdict.NOT_NEEDED, List.of("broadcast: " + why));
        }
        if (question || asks) {
            List<String> reasons = new ArrayList<>();
            if (question) reasons.add("asks a question");
            if (acts.contains("request")) reasons.add("contains a request");
            if (acts.contains("propose")) reasons.add("contains a proposal");
            return new Decision(Verdict.NEEDED, reasons);
        }
        List<String> content = text.lines().map(String::strip).filter(line -> !line.isEmpty())
                .filter(line -> !GREETING_OR_CLOSING.matcher(line).matches()).toList();
        int words = String.join(" ", content).split("\\s+").length;
        if (content.isEmpty() || (words <= SHORT_WORDS && THANKS.matcher(String.join(" ", content)).find())) {
            return new Decision(Verdict.NOT_NEEDED, List.of(content.isEmpty() ? "no text of its own"
                    : "only thanks or an acknowledgement"));
        }
        return new Decision(Verdict.NOT_NEEDED, List.of("no question, request or proposal: information only"));
    }

    private static List<String> automated(String sender, Map<String, String> headers, String text) {
        List<String> reasons = new ArrayList<>();
        String local = sender.contains("@") ? sender.substring(0, sender.indexOf('@')) : sender;
        if (!local.isEmpty() && AUTOMATED_SENDER.matcher(local).matches()) {
            reasons.add("automated sender (" + local + "@…)");
        }
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                String name = header.getKey().toLowerCase(Locale.ROOT);
                String value = header.getValue() == null ? "" : header.getValue().trim().toLowerCase(Locale.ROOT);
                if (name.equals("list-id") || name.equals("list-unsubscribe")) {
                    reasons.add("mailing-list header " + header.getKey());
                } else if (name.equals("precedence") && value.matches("bulk|list|junk")) {
                    reasons.add("bulk mail (Precedence: " + value + ")");
                } else if (name.equals("auto-submitted") && !value.isEmpty() && !value.equals("no")) {
                    reasons.add("automatically submitted (Auto-Submitted: " + value + ")");
                }
            }
        }
        if (AUTOMATED_TEXT.matcher(text).find()) {
            reasons.add("says it is automated or not to reply");
        }
        return reasons;
    }
}
