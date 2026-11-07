package org.supermas.agent.role;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.supermas.model.Domain.Evidence;

/**
 * What a specialist's knowledge says a request must contain, and the email does not: "The request must name the
 * course and include a medical certificate" against an email that names the course but has no certificate gives
 * "medical certificate". The rule check of the missing-information agent only knows the role's fixed fields, so
 * the reply never asked for the certificate (step 13, run 2; to-do 3.9).
 *
 * <p>Rules, not a model: a requirement sentence ("must include ...", "doit inclure ...") in a knowledge excerpt,
 * split into its items; an item is met when its words occur in the email, or, for the course and the student
 * number, when the analysis found that entity. Thread messages are not read as requirements.
 */
final class KnowledgeRequirements {
    private static final String KNOWLEDGE = "From knowledge source";
    private static final String VERBS_EN = "include|name|contain|provide|state|give|attach|enclose|mention";
    private static final String VERBS_FR = "inclure|contenir|indiquer|mentionner|fournir|joindre|comporter|preciser";
    private static final Pattern REQUIREMENT = Pattern.compile("\\b(?:must|has to|have to|needs? to|doit|doivent)\\s+(?:"
            + VERBS_EN + "|" + VERBS_FR + ")\\s+([^.;:!?\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPLIT = Pattern.compile("\\s*(?:,|\\band\\b|\\bet\\b)\\s*(?:(?:" + VERBS_EN + "|" + VERBS_FR
            + ")\\s+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTICLE = Pattern.compile("^(?:the|a|an|your|their|its|le|la|les|l'|un|une|des|du|votre|leur)\\s*",
            Pattern.CASE_INSENSITIVE);

    private KnowledgeRequirements() { }

    /**
     * @param presentEntities entity types the analysis found in the email (COURSE, ID, ...)
     * @return unmet items, each with the source that requires it, in the order found
     */
    static List<String> unmet(List<Evidence> context, String emailText, Set<String> presentEntities) {
        String email = emailText == null ? "" : emailText.toLowerCase(Locale.ROOT);
        Set<String> unmet = new LinkedHashSet<>();
        for (Evidence item : context) {
            if (item.provenance() == null || item.provenance().description() == null
                    || !item.provenance().description().startsWith(KNOWLEDGE)) {
                continue;
            }
            Matcher sentence = REQUIREMENT.matcher(item.text());
            while (sentence.find()) {
                for (String part : SPLIT.split(sentence.group(1))) {
                    String requirement = ARTICLE.matcher(part.strip()).replaceFirst("").strip();
                    if (!requirement.isEmpty() && !met(requirement, email, presentEntities)) {
                        unmet.add(requirement + " (required by " + item.provenance().sourceId() + ")");
                    }
                }
            }
        }
        return new ArrayList<>(unmet);
    }

    private static boolean met(String requirement, String email, Set<String> presentEntities) {
        String lower = requirement.toLowerCase(Locale.ROOT);
        if (lower.contains("course") || lower.contains("cours")) {
            return presentEntities.contains("COURSE") || email.contains(lower);
        }
        if (lower.contains("student number") || lower.contains("numero") || lower.contains("numéro")) {
            return presentEntities.contains("ID");
        }
        for (String word : lower.split("[^\\p{L}0-9]+")) {
            if (word.length() >= 4 && !email.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
