package org.supermas.context;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;
import org.supermas.prototype.InMemoryBlackboard;

/** A small project with its index, sources and agents, for testing context agents. */
final class TopicTestSupport implements AutoCloseable {
    static final String PROJECT = "project-a";
    static final String WIKI = "wiki";
    static final String MAIL = "mail";
    static final Scope HERE = new Scope(PROJECT, "thread-1");
    static final Instant AT = Instant.parse("2026-11-01T10:00:00Z");

    final ProjectIndexes indexes = ProjectIndexes.inMemory();
    final Map<String, AgentConfig> agents = new HashMap<>();
    final Map<String, ContextSource> sources = new HashMap<>();
    final LuceneContextRetriever retriever;

    /** Every source is open to these agents, so tests can narrow rights document by document. */
    TopicTestSupport(Set<String> agentIds) {
        for (String source : List.of(WIKI, MAIL)) {
            sources.put(source, new ContextSource(source, PROJECT, URI.create("file:///unused"), agentIds));
        }
        for (String agent : agentIds) {
            agents.put(agent, new AgentConfig(agent, "r", "", Set.of(), List.of(), Set.of(WIKI, MAIL), false));
        }
        retriever = new LuceneContextRetriever(indexes, sources, id -> Optional.ofNullable(agents.get(id)));
    }

    TopicDefinition subject(String agentId, String tag, List<String> keywords) {
        return new TopicDefinition(TopicType.SUBJECT, agentId, PROJECT, tag, keywords, Set.of(WIKI, MAIL), 10_000);
    }

    void document(String id, Scope scope, String source, Optional<MessageKind> kind, String text,
                  Set<String> permitted, Set<String> tags) {
        indexes.forProject(scope.projectId()).add(new IndexedDocument(id, scope, source, kind, "en", "", text, permitted,
                tags, AT));
    }

    static InMemoryBlackboard board(Scope scope, String subject, String currentText) {
        Email email = new Email("incoming", scope, "alice@example.org", List.of("me@example.org"), subject,
                currentText + "\n> quoted words", AT, Map.of());
        Analysis analysis = new Analysis(List.of(), List.of(), Map.of(MetadataAgent.KEY_CURRENT_TEXT, currentText));
        return new InMemoryBlackboard(new BoardId(scope, "task"), email, analysis, Clock.fixed(AT, ZoneOffset.UTC));
    }

    @Override
    public void close() throws Exception {
        indexes.close();
    }
}
