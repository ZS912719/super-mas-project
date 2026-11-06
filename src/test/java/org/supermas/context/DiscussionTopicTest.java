package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;
import org.supermas.prototype.InMemoryBlackboard;

class DiscussionTopicTest {
    private static final Scope HERE = TopicTestSupport.HERE;
    private static final String AGENT = "discussion";

    private final TopicTestSupport support = new TopicTestSupport(Set.of(AGENT, "other"));

    private TopicContextAgent agent(int budget) {
        return new TopicContextAgent(new TopicDefinition(TopicType.DISCUSSION, AGENT, TopicTestSupport.PROJECT, "",
                List.of(), Set.of(TopicTestSupport.MAIL), budget), support.retriever);
    }

    private void message(String id, Scope scope, MessageKind kind, Set<String> permitted, int minute) {
        support.indexes.forProject(scope.projectId()).add(new IndexedDocument(id, scope, TopicTestSupport.MAIL,
                Optional.of(kind), "en", "", "body of " + id, permitted, Set.of(),
                Instant.parse("2026-11-01T10:00:00Z").plusSeconds(minute * 60L)));
    }

    private List<String> found(TopicContextAgent agent) {
        InMemoryBlackboard board = TopicTestSupport.board(HERE, "s", "text");
        agent.execute(null, board.accessFor(agent.config()));
        return board.snapshot().evidence().stream().map(Evidence::id).map(TopicEvidence::documentId).toList();
    }

    @AfterEach
    void close() throws Exception {
        support.close();
    }

    @Test
    void itAlwaysVolunteersInItsOwnProject() {
        assertEquals(1, agent(100).assess(null, TopicTestSupport.board(HERE, "s", "t")).score());
        assertEquals(0, agent(100).assess(null, TopicTestSupport.board(new Scope("elsewhere", "thread-1"), "s", "t")).score());
    }

    @Test
    void itReturnsTheThreadNewestFirst() {
        message("old", HERE, MessageKind.RECEIVED, Set.of(AGENT), 1);
        message("new", HERE, MessageKind.SENT, Set.of(AGENT), 2);
        assertEquals(List.of("new", "old"), found(agent(1000)));
    }

    @Test
    void itStaysInsideTheThread() {
        message("mine", HERE, MessageKind.RECEIVED, Set.of(AGENT), 1);
        message("other-thread", new Scope(TopicTestSupport.PROJECT, "thread-2"), MessageKind.RECEIVED, Set.of(AGENT), 2);
        message("same-id-other-project", new Scope("project-b", "thread-1"), MessageKind.RECEIVED, Set.of(AGENT), 3);
        assertEquals(List.of("mine"), found(agent(1000)));
    }

    @Test
    void draftsAreNeverHistory() {
        message("draft", HERE, MessageKind.GENERATED_DRAFT, Set.of(AGENT), 1);
        message("edited", HERE, MessageKind.USER_EDITED_DRAFT, Set.of(AGENT), 2);
        message("real", HERE, MessageKind.RECEIVED, Set.of(AGENT), 3);
        assertEquals(List.of("real"), found(agent(1000)));
    }

    @Test
    void aThreadDoesNotBypassMessageRights() {
        message("hidden", HERE, MessageKind.RECEIVED, Set.of("other"), 1);
        message("visible", HERE, MessageKind.RECEIVED, Set.of(AGENT), 2);
        assertEquals(List.of("visible"), found(agent(1000)));
    }

    @Test
    void aThreadDoesNotBypassSourceRights() {
        message("from-mail", HERE, MessageKind.RECEIVED, Set.of(AGENT), 1);
        TopicContextAgent wikiOnly = new TopicContextAgent(new TopicDefinition(TopicType.DISCUSSION, AGENT,
                TopicTestSupport.PROJECT, "", List.of(), Set.of(TopicTestSupport.WIKI), 1000), support.retriever);
        // The retriever reads the agent's rights from trusted configuration, so narrow them there.
        support.agents.put(AGENT, wikiOnly.config());
        assertTrue(found(wikiOnly).isEmpty());
    }

    @Test
    void theBudgetDropsWholeMessagesKeepingTheNewest() {
        message("first", HERE, MessageKind.RECEIVED, Set.of(AGENT), 1);
        message("second", HERE, MessageKind.RECEIVED, Set.of(AGENT), 2);
        // Each text is "body of first" or "body of second": 13 and 14 characters.
        assertEquals(List.of("second"), found(agent(14)));
        assertTrue(found(agent(5)).isEmpty());
    }

    @Test
    void theEmailBeingAnsweredIsNotItsOwnContext() {
        message("incoming", HERE, MessageKind.RECEIVED, Set.of(AGENT), 1);
        assertTrue(found(agent(1000)).isEmpty());
    }

    @Test
    void aNewThreadIsANormalOutcome() {
        assertTrue(found(agent(1000)).isEmpty());
    }
}
