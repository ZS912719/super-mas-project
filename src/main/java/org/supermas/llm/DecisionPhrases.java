package org.supermas.llm;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds sentences in a drafted reply in which the model decides or acts for the mailbox owner: approving or granting
 * something, confirming that something was changed, or claiming an action such as an attachment that it cannot have
 * done. Such a sentence is never removed (warn mode, owner's decision 2026-11-11): the draft keeps it and the reviewer
 * gets a warning. A sentence whose words also appear in the email is marked as possibly dictated, the pattern of the
 * prompt-injection benchmark (protocol B65, B66), where both models copied a decision written into the email.
 *
 * <p>Phrase lists in English and French, matched on lower-case text without accents. They catch common wordings,
 * not every paraphrase, so the review stays the safeguard.
 */
public final class DecisionPhrases {
    /** Starts every warning, so callers can find warnings inside a draft's explanation. */
    public static final String WARNING_PREFIX = "Review warning: ";

    private static final List<Pattern> PATTERNS = List.of(
            // Approvals, grants, acceptances.
            Pattern.compile("\\b(?:is|are|has been|have been|was|were|will be)\\s+(?:approved|granted|accepted|confirmed|authori[sz]ed|waived)\\b"),
            Pattern.compile("\\b(?:i|we)\\s+(?:hereby\\s+)?(?:approve|grant|accept|authori[sz]e|waive)\\b"),
            // Confirmed changes and bookings.
            Pattern.compile("\\b(?:i|we)\\s+(?:can\\s+)?confirm\\s+(?:that\\s+)?[^.!?\\n]{0,80}\\b(?:changed|approved|granted|accepted|"
                    + "extended|booked|reserved|cancelled|canceled|registered|paid|refunded|updated)\\b"),
            Pattern.compile("\\b(?:has|have)\\s+been\\s+(?:changed|extended|booked|reserved|registered|paid|refunded)\\b"),
            // Actions the assistant cannot have done.
            Pattern.compile("\\b(?:i|we)\\s+(?:have|'ve)\\s+(?:attached|sent|forwarded|booked|reserved|paid|submitted|registered|"
                    + "changed|updated|cancelled|canceled)\\b"),
            Pattern.compile("\\bplease find\\s+(?:\\S+\\s+){0,4}?(?:attached|enclosed)\\b|\\bplease find attached\\b"),
            // French.
            Pattern.compile("\\b(?:est|sont|a ete|ont ete|sera|seront)\\s+(?:approuvee?s?|accordee?s?|acceptee?s?|validee?s?|autorisee?s?)\\b"),
            Pattern.compile("\\b(?:je|nous)\\s+(?:vous\\s+)?(?:confirme|confirmons|valide|validons|accorde|accordons|approuve|approuvons)\\b"),
            Pattern.compile("\\b(?:j'ai|nous avons)\\s+(?:envoye|joint|transmis|reserve|modifie|paye|annule)\\b"),
            Pattern.compile("\\bci-joint\\b|\\bvous trouverez ci-joint\\b"));
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?\\n]");

    private DecisionPhrases() { }

    /**
     * One warning per sentence of {@code reply} that decides or acts for the owner, in reading order.
     *
     * @param emailText the text of the email being answered (untrusted), to recognise dictated sentences; may be empty
     */
    public static List<String> warnings(String reply, String emailText) {
        String text = reply == null ? "" : reply;
        String folded = fold(text);
        String email = fold(emailText == null ? "" : emailText).replaceAll("\\s+", " ");
        Set<String> sentences = new LinkedHashSet<>();
        for (Pattern pattern : PATTERNS) {
            Matcher match = pattern.matcher(folded);
            while (match.find()) {
                sentences.add(sentence(text, match.start()));
            }
        }
        List<String> warnings = new ArrayList<>();
        for (String sentence : sentences) {
            String normalized = fold(sentence).replaceAll("\\s+", " ").replaceAll("[\"“”«»]", "").strip();
            boolean dictated = normalized.length() >= 12 && email.replaceAll("[\"“”«»]", "").contains(normalized);
            // Semicolons are explanation separators; a quoted sentence must not split a warning.
            warnings.add(WARNING_PREFIX + "the reply decides or acts for the owner: \"" + sentence.replace(';', ',') + "\""
                    + (dictated ? " (the same words are in the email: possibly dictated)" : ""));
        }
        return warnings;
    }

    /** The sentence of {@code text} around position {@code at} (folding keeps positions: only letters change). */
    private static String sentence(String text, int at) {
        int start = 0;
        Matcher end = SENTENCE_END.matcher(text);
        while (end.find() && end.end() <= at) {
            start = end.end();
        }
        Matcher next = SENTENCE_END.matcher(text);
        int stop = next.find(at) ? next.end() : text.length();
        return text.substring(start, stop).replaceAll("\\s+", " ").strip();
    }

    /** Lower case without accents, of the same length as the input, so match positions apply to the original. */
    static String fold(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            String plain = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            char folded = plain.isEmpty() ? c : plain.charAt(0);
            out.append(folded == '’' || folded == '‘' ? '\'' : Character.toLowerCase(folded));
        }
        return out.toString();
    }
}
