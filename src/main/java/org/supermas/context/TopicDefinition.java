package org.supermas.context;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One topic, from trusted configuration only. Nothing here may be built from email text. An email can match
 * a topic, but it cannot create or change one.
 *
 * @param tag the index tag that marks this topic's documents
 * @param keywords lower-case words or phrases that match an email to this topic
 * @param sourceIds what the topic's agent may read, still subject to every retriever check
 * @param maxCharacters this agent's evidence budget for one retrieval
 */
public record TopicDefinition(TopicType type, String agentId, String projectId, String tag, List<String> keywords,
                              Set<String> sourceIds, int maxCharacters) {
    public TopicDefinition {
        keywords = keywords.stream().map(keyword -> keyword.toLowerCase(Locale.ROOT)).toList();
        sourceIds = Set.copyOf(sourceIds);
    }
}
