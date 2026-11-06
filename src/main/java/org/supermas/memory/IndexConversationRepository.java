package org.supermas.memory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.supermas.context.IndexedDocument;
import org.supermas.context.ProjectIndex;
import org.supermas.context.ProjectIndexes;
import org.supermas.metadata.LanguageDetector;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.ConversationMessage;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;
import org.supermas.persistence.ConversationRepository;

/**
 * Conversation history kept in each project's search index, so that the context agents find it without a
 * second store. A message keeps its kind, which is what stops a draft from being read back as something sent.
 *
 * <p>Who may read a stored message comes from the project's conversation source in trusted configuration,
 * fixed when the message is written. Retrieval also intersects with the source's current readers, so removing
 * a reader from the source takes effect on old messages, while adding one applies only to new messages.
 */
public final class IndexConversationRepository implements ConversationRepository {
    private final ProjectIndexes indexes;
    private final Function<String, Optional<ContextSource>> conversationSource;
    private final LanguageDetector languages = new LanguageDetector();

    /**
     * @param conversationSource the source holding a project's mail, by project identifier. A project with none
     *                           cannot store history, which fails closed instead of storing mail nobody configured
     */
    public IndexConversationRepository(ProjectIndexes indexes,
                                       Function<String, Optional<ContextSource>> conversationSource) {
        this.indexes = indexes;
        this.conversationSource = conversationSource;
    }

    /** Stores the message unless one with its identifier is already stored. */
    @Override
    public void append(ConversationMessage message) {
        appendIfAbsent(message, Set.of());
    }

    /**
     * Stores the message with these topic tags. A repeated identical message is ignored, so a retried call is
     * harmless. The same identifier with another kind, scope or text is refused, because silently keeping
     * either one would lose a record, for example a confirmed send.
     *
     * @return true if the message was stored now, false if it was already there
     */
    public boolean appendIfAbsent(ConversationMessage message, Set<String> topicTags) {
        Scope scope = message.scope();
        ContextSource source = conversationSource.apply(scope.projectId())
                .filter(found -> found.projectId().equals(scope.projectId()))
                .orElseThrow(() -> new IllegalStateException("Project " + scope.projectId()
                        + " has no conversation source"));
        ProjectIndex index = indexes.forProject(scope.projectId());
        Optional<IndexedDocument> existing = index.find(message.id());
        if (existing.isPresent()) {
            IndexedDocument old = existing.get();
            boolean same = old.kind().equals(Optional.of(message.kind())) && old.scope().equals(scope)
                    && old.text().equals(text(message));
            if (!same) {
                throw new IllegalStateException("Message identifier " + message.id() + " is already used by another record");
            }
            return false;
        }
        index.add(new IndexedDocument(message.id(), scope, source.id(), Optional.of(message.kind()),
                languages.detect(text(message)), message.subject() == null ? "" : message.subject(), text(message),
                source.permittedAgentIds(), topicTags, message.recordedAt()));
        index.commit();
        return true;
    }

    /** Exactly this project and thread, only the requested kinds, oldest first, at most the newest maximum. */
    @Override
    public List<ConversationMessage> find(Scope scope, HistoryQuery query) {
        return indexes.forProject(scope.projectId()).thread(scope, query.kinds(), query.maxMessages()).stream()
                .map(document -> new ConversationMessage(document.id(), document.scope(), document.kind().orElseThrow(),
                        document.subject(), document.text(),
                        new Provenance(document.sourceId(), "Stored " + document.kind().orElseThrow().name()
                                + " message", document.recordedAt()), document.recordedAt()))
                .toList();
    }

    private static String text(ConversationMessage message) {
        return message.body() == null ? "" : message.body();
    }
}
