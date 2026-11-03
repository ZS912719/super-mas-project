package org.supermas.memory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.supermas.context.SenderVerifier;
import org.supermas.context.TopicTagger;
import org.supermas.model.Domain.Email;
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
    Set<String> tags(Scope scope, String subject, String text, Optional<Email> received);

    static MessageTagger none() {
        return (scope, subject, text, received) -> Set.of();
    }

    /** The declared subject topics whose keywords the message contains. */
    static MessageTagger subjects(TopicTagger topics) {
        return (scope, subject, text, received) -> topics.tagsFor(scope.projectId(), subject, text);
    }

    /** The sender's correspondent tag, only for a received email from a verified sender. */
    static MessageTagger correspondents(SenderVerifier verifier) {
        return (scope, subject, text, received) -> received
                .flatMap(email -> TopicTagger.correspondentTag(email, verifier)).map(Set::of).orElse(Set.of());
    }

    /** Every tag any of the taggers gives. */
    static MessageTagger all(List<MessageTagger> taggers) {
        List<MessageTagger> copy = List.copyOf(taggers);
        return (scope, subject, text, received) -> {
            Set<String> tags = new HashSet<>();
            copy.forEach(tagger -> tags.addAll(tagger.tags(scope, subject, text, received)));
            return tags;
        };
    }
}
