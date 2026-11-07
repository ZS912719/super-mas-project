package org.supermas.agent.role;

import java.util.List;
import java.util.Optional;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.ModelRequest;

/**
 * Builds one model request for a role. The trusted system prompt comes only from the role's configuration.
 * Everything taken from mail, documents, the plan or feedback goes into the untrusted data, in labelled blocks.
 *
 * <p>The whole request, system prompt included, stays within the role's input budget. The email text and the
 * feedback are not exempt: if they do not fit, they are cut and marked. Context fills what is left, whole items
 * in the selector's order, never cut.
 */
final class RolePromptBuilder {
    static final String TRUNCATED = " [truncated]";

    /** The request, and how much context made it in. */
    record Built(ModelRequest request, int contextIncluded, int contextOmitted, boolean truncated) { }

    private RolePromptBuilder() { }

    /**
     * @throws IllegalStateException if the system prompt and the plan alone exceed the budget, which is a
     *                               configuration error and not something to fix by cutting the plan
     */
    static Built build(String agentId, String systemPrompt, String sender, List<String> recipients, String subject,
                       String emailText, String planJson, List<Evidence> context, Optional<String> feedback,
                       int maxInputCharacters) {
        // Who wrote and who received the email: without it a model tends to greet the person addressed in the
        // email's own salutation, i.e. write to the recipient instead of as the recipient (step 13).
        String headerBlock = block("EMAIL ADDRESSES", "From (the sender, who receives the reply): "
                + oneLine(sender) + "\nTo (the recipient, on whose behalf "
                + "the reply is written): " + oneLine(recipients == null ? null : String.join(", ", recipients))) + block("EMAIL SUBJECT", subject);
        String planBlock = block("REPLY PLAN (JSON)", planJson);
        int fixed = systemPrompt.length() + headerBlock.length() + planBlock.length() + labelLength("EMAIL TEXT")
                + (feedback.isPresent() ? labelLength("FEEDBACK ON THE PREVIOUS DRAFT") : 0);
        int remaining = maxInputCharacters - fixed;
        if (remaining < 0) {
            throw new IllegalStateException("The system prompt and the plan exceed the input budget of " + agentId);
        }
        String text = clean(emailText);
        String note = feedback.map(RolePromptBuilder::clean).orElse("");
        boolean truncated = false;
        if (text.length() + note.length() > remaining) {
            truncated = true;
            int noteBudget = feedback.isPresent() ? Math.min(note.length(), remaining / 3) : 0;
            note = cut(note, noteBudget);
            text = cut(text, remaining - note.length());
        }
        StringBuilder data = new StringBuilder(headerBlock).append(rawBlock("EMAIL TEXT", text));
        if (feedback.isPresent()) {
            data.append(rawBlock("FEEDBACK ON THE PREVIOUS DRAFT", note));
        }
        data.append(planBlock);
        int included = 0;
        for (Evidence item : context) {
            String itemBlock = block("CONTEXT id=" + clean(item.id()) + " source=" + clean(item.provenance().sourceId())
                    + " kind=" + item.kind(), item.text());
            if (systemPrompt.length() + data.length() + itemBlock.length() <= maxInputCharacters) {
                data.append(itemBlock);
                included++;
            }
        }
        return new Built(new ModelRequest(agentId, systemPrompt, data.toString(), maxInputCharacters), included,
                context.size() - included, truncated);
    }

    /**
     * Untrusted text cannot open a block of its own, because the block marker is broken wherever it appears.
     * This does not make the text safe to obey; it keeps the labels honest.
     */
    static String clean(String text) {
        return text == null ? "" : text.replace("<<", "< <").replace(">>", "> >");
    }

    /** An address line from the email: untrusted, so kept to one line; "unknown" when absent. */
    private static String oneLine(String text) {
        return text == null || text.isBlank() ? "unknown" : text.replaceAll("\\R+", " ").strip();
    }

    private static String block(String label, String text) {
        return rawBlock(label, clean(text));
    }

    private static String rawBlock(String label, String cleanText) {
        return "<<" + label + ">>\n" + cleanText + "\n";
    }

    private static int labelLength(String label) {
        return rawBlock(label, "").length();
    }

    private static String cut(String text, int budget) {
        if (text.length() <= budget) {
            return text;
        }
        if (budget <= TRUNCATED.length()) {
            return "";
        }
        return text.substring(0, budget - TRUNCATED.length()) + TRUNCATED;
    }
}
