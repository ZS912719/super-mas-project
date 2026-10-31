package org.supermas.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.supermas.model.Domain.Email;

/**
 * Chooses a document's subject tags at indexing time, using the same matching as {@code assess}. Tags come
 * only from the configured topics. Text can place a document in a topic, but it cannot invent one, and a
 * tag never changes who may read the document.
 */
public final class TopicTagger {
    private final List<TopicDefinition> subjects;

    public TopicTagger(List<TopicDefinition> subjects) {
        this.subjects = List.copyOf(subjects);
    }

    public Set<String> tagsFor(String projectId, String subject, String text) {
        String normalized = TopicText.normalize(subject + " " + text);
        Set<String> tags = new HashSet<>();
        for (TopicDefinition topic : subjects) {
            if (topic.projectId().equals(projectId)
                    && topic.keywords().stream().anyMatch(keyword -> TopicText.contains(normalized, keyword))) {
                tags.add(topic.tag());
            }
        }
        return tags;
    }

    /** A tag for this message's sender, only if verified, so forged mail never joins someone's history. */
    public static Optional<String> correspondentTag(Email email, SenderVerifier verifier) {
        return verified(email, verifier) ? Optional.of(senderTag(email.sender())) : Optional.empty();
    }

    /** A verifier that fails counts as no, and a missing sender cannot be anyone. */
    static boolean verified(Email email, SenderVerifier verifier) {
        try {
            return email.sender() != null && !email.sender().isBlank() && verifier.isVerified(email);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Hashed so an address cannot enter an identifier as written, and normalized so case does not split a history. */
    static String senderTag(String sender) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sender.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return "correspondent-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
