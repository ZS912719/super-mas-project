package org.supermas.context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
}
