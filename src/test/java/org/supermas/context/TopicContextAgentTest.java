package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.Scope;
import org.supermas.model.Domain.TaskStatus;
import org.supermas.prototype.InMemoryBlackboard;

class TopicContextAgentTest {
    private static final Scope HERE = TopicTestSupport.HERE;
    private final TopicTestSupport support = new TopicTestSupport(Set.of("subject-a", "subject-b"));
    private final TopicContextAgent agent = new TopicContextAgent(
            support.subject("subject-a", "topic-a", List.of("budget", "cost report", "forecast")), support.retriever);

    private void doc(String id, String text, Set<String> tags) {
        support.document(id, HERE, TopicTestSupport.WIKI, Optional.empty(), text, Set.of("subject-a"), tags);
    }

    private AgentAssessment assess(String subject, String text) {
        return agent.assess(null, TopicTestSupport.board(HERE, subject, text));
    }

    @AfterEach
    void close() throws Exception {
        support.close();
    }

    @Test
    void theScoreGrowsWithDistinctKeywordsUpToOne() {
        assertEquals(0, assess("Hello", "Nothing relevant").score());
        assertEquals(1.0 / 3, assess("Budget", "the budget again").score(), 1e-9);
        assertEquals(2.0 / 3, assess("Budget", "and the COST REPORT").score(), 1e-9);
        assertEquals(1, assess("Budget forecast", "cost report").score());
    }

    @Test
    void keywordsMatchWholeWordsAndPhrases() {
        assertEquals(0, assess("Budgetary", "costs report forecasting").score());
    }

    @Test
    void quotedTextDoesNotActivateATopic() {
        // The fixture's body holds "quoted words", which must not be read when an analysis exists.
        TopicContextAgent quoted = new TopicContextAgent(
                support.subject("subject-b", "t", List.of("quoted")), support.retriever);
        assertEquals(0, quoted.assess(null, TopicTestSupport.board(HERE, "s", "new text")).score());
    }

    @Test
    void anotherProjectScoresZeroAndRetrievesNothing() {
        doc("doc", "budget", Set.of("topic-a"));
        InMemoryBlackboard elsewhere = TopicTestSupport.board(new Scope("project-b", "thread-1"), "budget", "budget");
        assertEquals(0, agent.assess(null, elsewhere).score());
        agent.execute(null, elsewhere.accessFor(agent.config()));
        assertTrue(elsewhere.snapshot().evidence().isEmpty());
    }

    @Test
    void executeSubmitsOnlyThisTopicsDocuments() {
        doc("in-topic", "budget numbers", Set.of("topic-a"));
        doc("other-topic", "budget numbers", Set.of("topic-z"));
        doc("untagged", "budget numbers", Set.of());
        InMemoryBlackboard board = TopicTestSupport.board(HERE, "budget", "budget");
        AgentOutcome outcome = agent.execute(null, board.accessFor(agent.config()));
        assertEquals(TaskStatus.SUCCEEDED, outcome.status());
        List<Evidence> evidence = board.snapshot().evidence();
        assertEquals(1, evidence.size());
        assertEquals("in-topic", TopicEvidence.documentId(evidence.get(0).id()));
        assertEquals("subject-a", TopicEvidence.agentId(evidence.get(0).id()));
    }

    @Test
    void aColdStartIsANormalOutcome() {
        InMemoryBlackboard board = TopicTestSupport.board(HERE, "budget", "budget");
        assertEquals(TaskStatus.SUCCEEDED, agent.execute(null, board.accessFor(agent.config())).status());
        assertTrue(board.snapshot().evidence().isEmpty());
        assertFalse(agent.assess(null, board).sufficientContext());
    }

    @Test
    void runningAgainAddsNothingAndReportsSufficient() {
        doc("doc", "budget", Set.of("topic-a"));
        InMemoryBlackboard board = TopicTestSupport.board(HERE, "budget", "budget");
        assertFalse(agent.assess(null, board).sufficientContext());
        agent.execute(null, board.accessFor(agent.config()));
        assertTrue(agent.assess(null, board.accessFor(agent.config())).sufficientContext());
        agent.execute(null, board.accessFor(agent.config()));
        assertEquals(1, board.snapshot().evidence().size());
    }

    @Test
    void twoTopicsFindingOneDocumentBothKeepTheirEntry() {
        support.document("shared", HERE, TopicTestSupport.WIKI, Optional.empty(), "budget",
                Set.of("subject-a", "subject-b"), Set.of("topic-a", "topic-b"));
        TopicContextAgent other = new TopicContextAgent(
                support.subject("subject-b", "topic-b", List.of("budget")), support.retriever);
        InMemoryBlackboard board = TopicTestSupport.board(HERE, "budget", "budget");
        agent.execute(null, board.accessFor(agent.config()));
        other.execute(null, board.accessFor(other.config()));
        assertEquals(2, board.snapshot().evidence().size());
        assertEquals(1, support.indexes.forProject(TopicTestSupport.PROJECT).size());
    }

    @Test
    void itCannotDraftAReply() {
        InMemoryBlackboard board = TopicTestSupport.board(HERE, "budget", "budget");
        assertThrows(SecurityException.class, () -> board.accessFor(agent.config())
                .submitDraft(new DraftProposal("s", "b", "subject-a", "e")));
    }

    @Test
    void theReasonNeverCarriesEmailText() {
        assertFalse(assess("budget", "secret-word budget").reason().contains("secret-word"));
        assertNotNull(assess("budget", "x").reason());
    }

    @Test
    void itsConfigurationComesFromTheTemplateOnly() {
        assertEquals("", agent.config().systemPrompt());
        assertEquals(Set.of(TopicContextAgent.CAPABILITY), agent.config().capabilities());
    }

    @Test
    void theTaggerUsesConfiguredTopicsOnly() {
        TopicTagger tagger = new TopicTagger(List.of(agent.topic(),
                new TopicDefinition(TopicType.SUBJECT, "x", "project-b", "other-project", List.of("budget"), Set.of(), 1)));
        assertEquals(Set.of("topic-a"), tagger.tagsFor(TopicTestSupport.PROJECT, "the budget", "invented-topic"));
        assertEquals(Set.of(), tagger.tagsFor(TopicTestSupport.PROJECT, "", "budgetary"));
    }
}
