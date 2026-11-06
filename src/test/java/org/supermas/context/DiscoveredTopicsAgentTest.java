package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.supermas.context.TopicDiscovery.ThreadText;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;
import org.supermas.prototype.Capabilities;
import org.supermas.prototype.ContextFirstCoordinationStrategy;
import org.supermas.prototype.DraftFirstCoordinationStrategy;
import org.supermas.prototype.InMemoryAgentRegistry;
import org.supermas.prototype.InMemoryBlackboard;
import org.supermas.prototype.InMemoryBlackboardFactory;
import org.supermas.prototype.ScoreRoutingStrategy;
import org.supermas.prototype.ScriptedCoordinator;

class DiscoveredTopicsAgentTest {
    private static final String AUTO = "context-discovered";
    private static final String REPLY = "reply";

    private final TopicTestSupport support = new TopicTestSupport(Set.of(AUTO, REPLY));
    private final TopicCatalog catalog = TopicCatalog.inMemory();
    private DiscoveredTopicsAgent agent;

    @AfterEach
    void close() throws Exception {
        support.close();
    }

    /** The three synthetic groups of step 10.1, as mail. The first room-booking thread is hidden from the agent. */
    private void regrouped() {
        for (ThreadText thread : TopicDiscoveryTest.threads()) {
            Set<String> readers = thread.threadId().equals("g1-t0") ? Set.of(REPLY) : Set.of(AUTO, REPLY);
            support.document(thread.threadId() + "-m", new Scope(TopicTestSupport.PROJECT, thread.threadId()),
                    TopicTestSupport.MAIL, Optional.of(MessageKind.RECEIVED), thread.text(), readers, Set.of());
        }
        new TopicRegrouper(support.indexes, catalog, new TopicDiscovery(TopicDiscovery.Settings.defaults()))
                .refresh(TopicTestSupport.PROJECT);
        AgentConfig config = new AgentConfig(AUTO, "context-discovered", "", Set.of(TopicContextAgent.CAPABILITY),
                List.of(), Set.of(TopicTestSupport.MAIL), false);
        support.agents.put(AUTO, config);
        agent = new DiscoveredTopicsAgent(config, TopicTestSupport.PROJECT, catalog, support.retriever, 2, 100_000);
    }

    private static List<String> documents(BlackboardSnapshot state) {
        return state.evidence().stream().filter(TopicEvidence::isTopicEvidence)
                .map(item -> TopicEvidence.documentId(item.id())).toList();
    }

    @Test
    void anEmailAboutRoomsGetsRoomMailItMayRead() {
        regrouped();
        InMemoryBlackboard board = TopicTestSupport.board(TopicTestSupport.HERE, "Booking",
                "Can I book the auditorium? Does it have a projector?");

        assertTrue(agent.assess(null, board).score() > 0);
        agent.execute(null, board.accessFor(agent.config()));

        List<String> found = documents(board.snapshot());
        assertFalse(found.isEmpty());
        assertTrue(found.stream().allMatch(id -> id.startsWith("g1-")), found.toString());
        assertFalse(found.contains("g1-t0-m"), "a hidden message stays hidden");
        String label = TopicEvidence.agentId(board.snapshot().evidence().get(0).id());
        assertTrue(label.startsWith(AUTO + ".t"), label);
    }

    @Test
    void anUnrelatedEmailFitsNoTopicAndAnotherProjectScoresZero() {
        regrouped();
        assertEquals(0, agent.assess(null, TopicTestSupport.board(TopicTestSupport.HERE, "Lunch",
                "Shall we have lunch at noon?")).score());
        assertEquals(0, agent.assess(null, TopicTestSupport.board(new Scope("elsewhere", "t"), "Booking",
                "auditorium projector booking")).score());
    }

    @Test
    void anEmailInAClusteredThreadFitsItsTopicFully() {
        regrouped();
        AgentAssessment assessment = agent.assess(null, TopicTestSupport.board(
                new Scope(TopicTestSupport.PROJECT, "g2-t3"), "Re: hello", "ok"));
        assertEquals(1, assessment.score());
    }

    @Test
    void anIdentifierWithADotIsRefused() {
        AgentConfig config = new AgentConfig("a.b", "r", "", Set.of(), List.of(), Set.of(), false);
        assertThrows(IllegalArgumentException.class,
                () -> new DiscoveredTopicsAgent(config, "p", catalog, support.retriever, 2, 1000));
    }

    @Test
    void theLoopRunsTheAgentOnceBeforeTheReplyAndTheMergeKeepsItsLists() {
        regrouped();
        SpecializedAgent reply = new SpecializedAgent() {
            private final AgentConfig config = new AgentConfig(REPLY, "reply", "", Set.of(Capabilities.DRAFT_REPLY),
                    List.of(), Set.of(TopicTestSupport.MAIL), false);

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
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(agent);
        registry.register(reply);
        Clock clock = Clock.fixed(TopicTestSupport.AT, ZoneOffset.UTC);
        ScriptedCoordinator coordinator = new ScriptedCoordinator(MetadataAgent.rulesOnly(),
                new InMemoryBlackboardFactory(clock), registry, new ScoreRoutingStrategy(0.5, 0.1),
                new ContextFirstCoordinationStrategy(List.of(), List.of(agent), new TopicCandidateFilter(3),
                        Optional.empty(), new DraftFirstCoordinationStrategy(), 2), clock);
        BoardId id = coordinator.receive(new Email("incoming", TopicTestSupport.HERE, "a@b.c", List.of(), "Grant",
                "Is the grant proposal budget ready for the reviewers?", TopicTestSupport.AT, Map.of()));

        BlackboardSnapshot state = coordinator.run(id, new ExecutionLimits(10, 0, 1, 100_000, 100_000));

        assertEquals(BoardStatus.AWAITING_REVIEW, state.status());
        assertEquals(1, state.tasks().stream().filter(task -> task.agentId().filter(AUTO::equals).isPresent()).count());
        List<Evidence> merged = new TopicContextSelector().select(() -> state,
                support.agents.get(REPLY), 100_000).evidence();
        assertFalse(merged.isEmpty());
        assertTrue(merged.stream().allMatch(item -> item.id().startsWith("g2-")), merged.toString());
    }
}
