package org.supermas.memory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.supermas.context.SenderVerifier;
import org.supermas.context.TopicCatalog;
import org.supermas.context.TopicTagger;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;

/**
 * Chooses the topic tags of a message when it is stored. Tags help a topic find the message. They never
 * change who may read it.
 */
@FunctionalInterface
public interface MessageTagger {
    /**
     * @param received the email itself when the message was received, which a correspondent tag needs;
     *                 empty for drafts and sent messages
     */
    Set<String> tags(Scope scope, MessageKind kind, String subject, String text, Optional<Email> received);

    static MessageTagger none() {
        return (scope, kind, subject, text, received) -> Set.of();
    }

    /** The declared subject topics whose keywords the message contains. */
    static MessageTagger subjects(TopicTagger topics) {
        return (scope, kind, subject, text, received) -> topics.tagsFor(scope.projectId(), subject, text);
    }

    /** The sender's correspondent tag, only for a received email from a verified sender. */
    static MessageTagger correspondents(SenderVerifier verifier) {
        return (scope, kind, subject, text, received) -> received
                .flatMap(email -> TopicTagger.correspondentTag(email, verifier)).map(Set::of).orElse(Set.of());
    }

    /**
     * The discovered topics a new message belongs to: its thread's topic, or topics whose keywords it contains.
     * Drafts get none, as in regrouping, because they are not mail anyone exchanged.
     */
    static MessageTagger discovered(TopicCatalog catalog) {
        return (scope, kind, subject, text, received) -> kind == MessageKind.RECEIVED || kind == MessageKind.SENT
                ? catalog.tagsFor(scope.projectId(), scope.threadId(), subject == null ? "" : subject, text)
                : Set.of();
    }

    /** Every tag any of the taggers gives. */
    static MessageTagger all(List<MessageTagger> taggers) {
        List<MessageTagger> copy = List.copyOf(taggers);
        return (scope, kind, subject, text, received) -> {
            Set<String> tags = new HashSet<>();
            copy.forEach(tagger -> tags.addAll(tagger.tags(scope, kind, subject, text, received)));
            return tags;
        };
    }
}
