package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.context.TopicCatalog.DiscoveredTopic;
import org.supermas.context.TopicDiscovery.Cluster;
import org.supermas.context.TopicDiscovery.ThreadText;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;

class TopicRegrouperTest {
    private static final String PROJECT = "p";
    private static final Instant AT = Instant.parse("2026-11-03T10:00:00Z");

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();
    private final TopicRegrouper regrouper = new TopicRegrouper(indexes, TopicCatalog.inMemory(),
            new TopicDiscovery(TopicDiscovery.Settings.defaults()));

    @TempDir
    Path folder;

    @AfterEach
    void close() throws Exception {
        indexes.close();
    }

    private void add(String id, String thread, Optional<MessageKind> kind, String text) {
        indexes.forProject(PROJECT).add(new IndexedDocument(id, new Scope(PROJECT, thread), "mail", kind, "en", "",
                text, Set.of("reader"), Set.of("declared-tag"), AT));
    }

    private void mail() {
        for (ThreadText thread : TopicDiscoveryTest.threads()) {
            add(thread.threadId() + "-m", thread.threadId(), Optional.of(MessageKind.RECEIVED), thread.text());
        }
    }

    private Set<String> tags(String id) {
        return indexes.forProject(PROJECT).find(id).orElseThrow().topicTags();
    }

    @Test
    void mailIsTaggedByItsThreadsTopicAndDeclaredTagsStay() {
        mail();
        TopicRegrouper.Result result = regrouper.refresh(PROJECT);

        assertEquals(3, result.topics());
        String tag = regrouper.topicOf(PROJECT, "g1-t0").orElseThrow().tag();
        assertEquals(Set.of("declared-tag", tag), tags("g1-t0-m"));
        assertTrue(tag.startsWith(TopicCatalog.TAG_PREFIX));
    }

    @Test
    void knowledgeJoinsByKeywordsAndDraftsNeverJoin() {
        mail();
        add("kb", "kb:rules", Optional.empty(), "Room booking: the projector and the auditorium are on floor two.");
        add("draft", "g1-t0", Optional.of(MessageKind.GENERATED_DRAFT), "room booking projector auditorium");
        regrouper.refresh(PROJECT);

        assertEquals(Set.of("declared-tag", regrouper.topicOf(PROJECT, "g1-t0").orElseThrow().tag()), tags("kb"));
        assertEquals(Set.of("declared-tag"), tags("draft"));
    }

    @Test
    void regroupingAgainKeepsIdentifiersAndChangesNothing() {
        mail();
        regrouper.refresh(PROJECT);
        List<DiscoveredTopic> before = regrouper.catalog().topics(PROJECT);

        TopicRegrouper.Result again = regrouper.refresh(PROJECT);

        assertEquals(before, regrouper.catalog().topics(PROJECT));
        assertEquals(0, again.documentsRetagged());
    }

    @Test
    void aChangedTopicKeepsItsIdentifierAndANewOneGetsANewIdentifier() {
        TopicCatalog catalog = TopicCatalog.inMemory();
        catalog.update(PROJECT, List.of(new Cluster(List.of("exam"), Set.of("a", "b", "c", "d"))));
        String old = catalog.topics(PROJECT).get(0).id();

        List<DiscoveredTopic> updated = catalog.update(PROJECT, List.of(
                new Cluster(List.of("exam", "retake"), Set.of("a", "b", "c", "e")),
                new Cluster(List.of("room"), Set.of("x", "y", "z"))));

        assertEquals(old, updated.stream().filter(t -> t.keywords().contains("retake")).findFirst().orElseThrow().id());
        assertTrue(updated.stream().filter(t -> t.keywords().contains("room")).noneMatch(t -> t.id().equals(old)));
    }

    @Test
    void identifiersSurviveARestart() {
        TopicCatalog first = TopicCatalog.onDisk(folder);
        List<DiscoveredTopic> topics = first.update(PROJECT, List.of(new Cluster(List.of("exam"), Set.of("a", "b", "c"))));

        TopicCatalog reopened = TopicCatalog.onDisk(folder);
        assertEquals(topics, reopened.topics(PROJECT));
        List<DiscoveredTopic> next = reopened.update(PROJECT, List.of(new Cluster(List.of("room"), Set.of("x", "y", "z"))));
        assertTrue(next.stream().noneMatch(t -> t.id().equals(topics.get(0).id())), "numbers are not reused");
    }

    @Test
    void identifiersAndTagsNeverContainMailText() {
        mail();
        regrouper.refresh(PROJECT);
        for (DiscoveredTopic topic : regrouper.catalog().topics(PROJECT)) {
            assertTrue(topic.id().matches("t[0-9]+"), topic.id());
            assertEquals(TopicCatalog.TAG_PREFIX + topic.id(), topic.tag());
        }
    }

    @Test
    void anEmptyProjectHasNoTopics() {
        assertEquals(0, regrouper.refresh(PROJECT).topics());
        assertEquals(new ArrayList<>(), regrouper.catalog().topics(PROJECT));
    }
}
