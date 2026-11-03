package org.supermas.context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.supermas.context.TopicCatalog.DiscoveredTopic;
import org.supermas.context.TopicDiscovery.ThreadText;
import org.supermas.model.Domain.MessageKind;

/**
 * Rebuilds a project's discovered topics from its mail and updates the index tags to match, with nothing for the
 * user to do. Only received and sent mail is clustered, because a draft is not something anyone said.
 * Knowledge documents and messages in threads without a topic join a topic by containing enough of its keywords.
 */
public final class TopicRegrouper {
    private static final Set<MessageKind> CLUSTERED = Set.of(MessageKind.RECEIVED, MessageKind.SENT);

    /** What one regrouping did. */
    public record Result(int topics, int threadsClustered, int threadsSeen, int documentsRetagged, long millis) { }

    private final ProjectIndexes indexes;
    private final TopicCatalog catalog;
    private final TopicDiscovery discovery;

    public TopicRegrouper(ProjectIndexes indexes, TopicCatalog catalog, TopicDiscovery discovery) {
        this.indexes = indexes;
        this.catalog = catalog;
        this.discovery = discovery;
    }

    public TopicCatalog catalog() {
        return catalog;
    }

    public synchronized Result refresh(String projectId) {
        long start = System.nanoTime();
        ProjectIndex index = indexes.forProject(projectId);
        List<IndexedDocument> documents = index.all();
        Map<String, StringBuilder> threads = new LinkedHashMap<>();
        for (IndexedDocument document : documents) {
            if (document.kind().filter(CLUSTERED::contains).isPresent()) {
                StringBuilder text = threads.computeIfAbsent(document.scope().threadId(),
                        id -> new StringBuilder(document.subject()).append('\n'));
                text.append(document.text()).append('\n');
            }
        }
        List<ThreadText> texts = new ArrayList<>();
        threads.forEach((id, text) -> texts.add(new ThreadText(id, text.toString())));
        List<DiscoveredTopic> topics = catalog.update(projectId, discovery.discover(texts));

        List<IndexedDocument> changed = new ArrayList<>();
        for (IndexedDocument document : documents) {
            Set<String> tags = new HashSet<>();
            document.topicTags().stream().filter(tag -> !tag.startsWith(TopicCatalog.TAG_PREFIX)).forEach(tags::add);
            boolean draft = document.kind().filter(kind -> !CLUSTERED.contains(kind)).isPresent();
            if (!draft) {
                tags.addAll(catalog.tagsFor(projectId, document.kind().isPresent() ? document.scope().threadId() : null,
                        document.subject(), document.text()));
            }
            if (!tags.equals(document.topicTags())) {
                changed.add(new IndexedDocument(document.id(), document.scope(), document.sourceId(), document.kind(),
                        document.language(), document.subject(), document.text(), document.permittedAgentIds(), tags,
                        document.recordedAt()));
            }
        }
        if (!changed.isEmpty()) {
            index.addAll(changed);
            index.commit();
        }
        int clustered = topics.stream().mapToInt(topic -> topic.threadIds().size()).sum();
        return new Result(topics.size(), clustered, texts.size(), changed.size(),
                (System.nanoTime() - start) / 1_000_000);
    }

    /** Topics of the thread with this identifier, for tests and reports. */
    public Optional<DiscoveredTopic> topicOf(String projectId, String threadId) {
        return catalog.topics(projectId).stream().filter(topic -> topic.threadIds().contains(threadId)).findFirst();
    }
}
