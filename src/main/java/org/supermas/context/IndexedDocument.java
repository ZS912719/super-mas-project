package org.supermas.context;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;

/**
 * One searchable item in a project's index. Its text is untrusted. A message in a conversation has a
 * {@code kind}, while a domain document has none, so it can never be returned as thread history.
 *
 * @param permittedAgentIds who may ever receive this item: empty means no agent
 * @param participants      lower-case addresses of the sender and recipients of a received message, used only
 *                          to decide whether a later reply without reply headers belongs to its thread; empty
 *                          for drafts, sent records and documents
 */
public record IndexedDocument(String id, Scope scope, String sourceId, Optional<MessageKind> kind, String language,
                              String subject, String text, Set<String> permittedAgentIds, Set<String> topicTags,
                              Instant recordedAt, Set<String> participants) {
    public IndexedDocument {
        permittedAgentIds = Set.copyOf(permittedAgentIds);
        topicTags = Set.copyOf(topicTags);
        participants = Set.copyOf(participants);
    }

    /** An item without participants: a document, a draft or a sent record. */
    public IndexedDocument(String id, Scope scope, String sourceId, Optional<MessageKind> kind, String language,
                           String subject, String text, Set<String> permittedAgentIds, Set<String> topicTags,
                           Instant recordedAt) {
        this(id, scope, sourceId, kind, language, subject, text, permittedAgentIds, topicTags, recordedAt, Set.of());
    }
}
