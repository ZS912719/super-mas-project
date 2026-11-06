package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextQuery;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.Scope;

class TopicTagRetrievalTest {
    private static final Scope HERE = new Scope("project-a", "thread-1");
    private static final ContextQuery QUERY = new ContextQuery(new BoardId(HERE, "task"), "agent",
            Set.of("wiki", "mail"), List.of("budget"), false, 1000);

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();
    private final LuceneContextRetriever retriever = new LuceneContextRetriever(indexes,
            Map.of("wiki", new ContextSource("wiki", "project-a", URI.create("file:///unused"), Set.of("agent")),
                    "mail", new ContextSource("mail", "project-a", URI.create("file:///unused"), Set.of("other"))),
            id -> Optional.of(new AgentConfig(id, "r", "p", Set.of(), List.of(), Set.of("wiki", "mail"), false)));

    private void add(String id, String source, Set<String> permitted, Set<String> tags) {
        indexes.forProject("project-a").add(new IndexedDocument(id, HERE, source, Optional.empty(), "en", "", "budget",
                permitted, tags, Instant.parse("2026-11-01T10:00:00Z")));
    }

    private List<String> found(Set<String> tags) {
        return retriever.retrieveDomain(QUERY, tags).evidence().stream().map(Evidence::id).sorted().toList();
    }

    @AfterEach
    void close() throws Exception {
        indexes.close();
    }

    @Test
    void onlyDocumentsWithARequestedTagAreReturned() {
        add("course", "wiki", Set.of("agent"), Set.of("course-cs204"));
        add("project", "wiki", Set.of("agent"), Set.of("project-x"));
        add("untagged", "wiki", Set.of("agent"), Set.of());
        assertEquals(List.of("course"), found(Set.of("course-cs204")));
        assertEquals(List.of("course", "project"), found(Set.of("course-cs204", "project-x")));
        assertEquals(List.of("course", "project", "untagged"), found(Set.of()));
    }

    @Test
    void oneDocumentCanBelongToSeveralTopicsWithoutBeingCopied() {
        add("both", "wiki", Set.of("agent"), Set.of("a", "b"));
        assertEquals(List.of("both"), found(Set.of("a")));
        assertEquals(List.of("both"), found(Set.of("b")));
        assertEquals(1, indexes.forProject("project-a").size());
    }

    @Test
    void aTopicFilterNeverWidensPermissions() {
        add("hidden-document", "wiki", Set.of("other"), Set.of("topic"));
        add("hidden-source", "mail", Set.of("agent"), Set.of("topic"));
        assertTrue(found(Set.of("topic")).isEmpty());
    }

    @Test
    void aTagMatchesExactly() {
        add("doc", "wiki", Set.of("agent"), Set.of("course-cs204"));
        assertTrue(found(Set.of("COURSE-CS204")).isEmpty());
        assertTrue(found(Set.of("course")).isEmpty());
    }

    @Test
    void retaggingChangesTopicsAndKeepsEverythingElse() {
        add("doc", "wiki", Set.of("agent"), Set.of("old"));
        assertTrue(indexes.forProject("project-a").retag("doc", Set.of("new")));
        assertTrue(found(Set.of("old")).isEmpty());
        assertEquals(List.of("doc"), found(Set.of("new")));
        assertEquals(1, indexes.forProject("project-a").size());
        assertFalse(indexes.forProject("project-a").retag("missing", Set.of("x")));
    }
}
