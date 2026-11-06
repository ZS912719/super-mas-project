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
 */
public record IndexedDocument(String id, Scope scope, String sourceId, Optional<MessageKind> kind, String language,
                              String subject, String text, Set<String> permittedAgentIds, Set<String> topicTags,
                              Instant recordedAt) {
    public IndexedDocument {
        permittedAgentIds = Set.copyOf(permittedAgentIds);
        topicTags = Set.copyOf(topicTags);
    }
}
