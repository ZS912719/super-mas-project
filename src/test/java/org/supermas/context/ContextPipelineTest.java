package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.AgentBlackboard;
import org.supermas.blackboard.BlackboardView;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;
import org.supermas.prototype.Capabilities;
import org.supermas.prototype.ContextFirstCoordinationStrategy;
import org.supermas.prototype.DraftFirstCoordinationStrategy;
import org.supermas.prototype.InMemoryAgentRegistry;
import org.supermas.prototype.InMemoryBlackboardFactory;
import org.supermas.prototype.ScoreRoutingStrategy;
import org.supermas.prototype.ScriptedCoordinator;

/** The whole loop: preprocessing, topic agents, the check, further retrieval, then routing and a draft. */
class ContextPipelineTest {
    private static final String SUBJECT = "subject";
    private static final String DISCUSSION = "discussion";
    private static final String CHECK = "check";
    private static final String REPLY = "reply";

    private final TopicTestSupport support = new TopicTestSupport(Set.of(SUBJECT, DISCUSSION, REPLY));

    private final SpecializedAgent reply = new SpecializedAgent() {
        private final AgentConfig config = new AgentConfig(REPLY, "reply", "", Set.of(Capabilities.DRAFT_REPLY), List.of(),
                Set.of(), false);

        @Override
        public AgentConfig config() {
            return config;
        }

        @Override
        public AgentAssessment assess(Task task, BlackboardView board) {
            return new AgentAssessment(REPLY, 1, true, "always");
        }

        @Override
        public AgentOutcome execute(Task task, AgentBlackboard board) {
            board.submitDraft(new DraftProposal("s", "b", REPLY, "e"));
            return new AgentOutcome(TaskStatus.SUCCEEDED, "drafted", List.of(), Optional.empty());
        }
    };

    private BlackboardSnapshot run(String text, int maxRounds) {
        return run("Budget", text, maxRounds);
    }

    private BlackboardSnapshot run(String emailSubject, String text, int maxRounds) {
        TopicContextAgent subject = new TopicContextAgent(support.subject(SUBJECT, "topic", List.of("budget")), support.retriever);
        TopicContextAgent discussion = new TopicContextAgent(new TopicDefinition(TopicType.DISCUSSION, DISCUSSION,
                TopicTestSupport.PROJECT, "", List.of(), Set.of(TopicTestSupport.MAIL), 10_000), support.retriever);
        MissingInformationAgent check = new MissingInformationAgent(CHECK, new RuleMissingInformationCheck(), Set.of(REPLY));
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(subject);
        registry.register(discussion);
        registry.register(check);
        registry.register(reply);
        Clock clock = Clock.fixed(TopicTestSupport.AT, ZoneOffset.UTC);
        ScriptedCoordinator coordinator = new ScriptedCoordinator(MetadataAgent.rulesOnly(), new InMemoryBlackboardFactory(clock),
                registry, new ScoreRoutingStrategy(0.5, 0.1), new ContextFirstCoordinationStrategy(List.of(subject, discussion),
                new TopicCandidateFilter(3), Optional.of(CHECK), new DraftFirstCoordinationStrategy(), 2), clock);
        BoardId id = coordinator.receive(new Email("incoming", TopicTestSupport.HERE, "a@b.c", List.of(), emailSubject, text,
                TopicTestSupport.AT, Map.of()));
        return coordinator.run(id, new ExecutionLimits(maxRounds, 0, 1, 100_000, 100_000));
    }

    private void seedDocuments() {
        support.document("found-at-first", TopicTestSupport.HERE, TopicTestSupport.WIKI, Optional.empty(), "budget figures",
                Set.of(SUBJECT), Set.of("topic"));
        support.document("only-with-need", TopicTestSupport.HERE, TopicTestSupport.WIKI, Optional.empty(), "calendar overview",
                Set.of(SUBJECT), Set.of("topic"));
        support.document("thread-message", TopicTestSupport.HERE, TopicTestSupport.MAIL, Optional.of(MessageKind.RECEIVED),
                "earlier words", Set.of(DISCUSSION), Set.of());
    }

    private static List<String> documents(BlackboardSnapshot state) {
        return state.evidence().stream().filter(TopicEvidence::isTopicEvidence)
                .map(item -> TopicEvidence.documentId(item.id())).toList();
    }

    private static long furtherRetrievals(BlackboardSnapshot state) {
        return state.tasks().stream().filter(task -> task.need().isPresent()).count();
    }

    @AfterEach
    void close() throws Exception {
        support.close();
    }

    @Test
    void aMissingDateAddsOneFurtherRetrievalAndTheLoopFinishes() {
        seedDocuments();
        BlackboardSnapshot state = run("Could you meet about this?", 20);
        assertEquals(BoardStatus.AWAITING_REVIEW, state.status());
        assertEquals(1, furtherRetrievals(state));
        assertEquals(TaskStatus.SUCCEEDED, state.tasks().stream().filter(task -> task.need().isPresent())
                .findFirst().orElseThrow().status());
        assertTrue(documents(state).containsAll(List.of("found-at-first", "only-with-need", "thread-message")));
        Evidence inference = state.evidence().stream().filter(item -> item.kind() == EvidenceKind.MODEL_INFERENCE)
                .findFirst().orElseThrow();
        assertEquals("Possibly missing: DATE", inference.text());
    }

    @Test
    void nothingMissingMeansNoFurtherRetrieval() {
        seedDocuments();
        BlackboardSnapshot state = run("Could you meet on January 12, 2027?", 20);
        assertEquals(BoardStatus.AWAITING_REVIEW, state.status());
        assertEquals(0, furtherRetrievals(state));
        assertFalse(documents(state).contains("only-with-need"));
    }

    @Test
    void contextNeverUsesTheRoundsTheReplyNeeds() {
        seedDocuments();
        for (int rounds = 2; rounds <= 7; rounds++) {
            BlackboardSnapshot state = run("Could you meet about this?", rounds);
            assertEquals(BoardStatus.AWAITING_REVIEW, state.status(), "rounds " + rounds);
            assertEquals(1, state.drafts().size(), "rounds " + rounds);
            assertTrue(state.budgetUsage().rounds() <= rounds, "rounds " + rounds);
        }
    }

    @Test
    void aReplyAgentCanReadTheInference() {
        seedDocuments();
        BlackboardSnapshot state = run("Could you meet about this?", 20);
        assertTrue(state.evidence().stream().filter(item -> item.kind() == EvidenceKind.MODEL_INFERENCE)
                .allMatch(item -> item.permittedAgentIds().contains(REPLY)));
    }

    @Test
    void theCoordinatorChoosesWhichAgentCarriesOutASuggestion() {
        seedDocuments();
        BlackboardSnapshot state = run("Could you meet about this?", 20);
        Task further = state.tasks().stream().filter(task -> task.need().isPresent()).findFirst().orElseThrow();
        assertEquals(Action.RETRIEVE_CONTEXT, further.action());
        assertEquals(Optional.of(SUBJECT), further.agentId());
    }

    @Test
    void aThreadOnlyEmailStillFinishesWithTheNeedLeftPending() {
        BlackboardSnapshot state = run("Hello", "Could you meet?", 20);
        assertEquals(BoardStatus.AWAITING_REVIEW, state.status());
        Task further = state.tasks().stream().filter(task -> task.need().isPresent()).findFirst().orElseThrow();
        assertEquals(TaskStatus.PENDING, further.status());
    }
}
