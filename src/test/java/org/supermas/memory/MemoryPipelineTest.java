package org.supermas.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.context.LuceneContextRetriever;
import org.supermas.context.ProjectIndexes;
import org.supermas.context.TopicCandidateFilter;
import org.supermas.context.TopicContextAgent;
import org.supermas.context.TopicDefinition;
import org.supermas.context.TopicEvidence;
import org.supermas.context.TopicType;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;
import org.supermas.prototype.Capabilities;
import org.supermas.prototype.ContextFirstCoordinationStrategy;
import org.supermas.prototype.DraftFirstCoordinationStrategy;
import org.supermas.prototype.InMemoryAgentRegistry;
import org.supermas.prototype.InMemoryBlackboardFactory;
import org.supermas.prototype.ScoreRoutingStrategy;
import org.supermas.prototype.ScriptedCoordinator;

/** Memory in the loop: what one email leaves behind is context for the next one in its thread. */
class MemoryPipelineTest {
    private static final String PROJECT = "project";
    private static final String MAIL = "mail";
    private static final String DISCUSSION = "discussion";
    private static final String REPLY = "reply";
    private static final Scope THREAD = new Scope(PROJECT, "thread");
    private static final Instant AT = Instant.parse("2026-11-03T10:00:00Z");
    private static final Map<String, ContextSource> SOURCES = Map.of(MAIL,
            new ContextSource(MAIL, PROJECT, URI.create("file:///unused"), Set.of(DISCUSSION, REPLY)));

    @TempDir
    Path folder;

    private final SpecializedAgent reply = new SpecializedAgent() {
        private final AgentConfig config = new AgentConfig(REPLY, "reply", "", Set.of(Capabilities.DRAFT_REPLY),
                List.of(), Set.of(), false);

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
            board.submitDraft(new DraftProposal("Re", "unsent draft words", REPLY, "e"));
            return new AgentOutcome(TaskStatus.SUCCEEDED, "drafted", List.of(), Optional.empty());
        }
    };

    private record Setup(ProjectIndexes indexes, ScriptedCoordinator coordinator, IndexConversationRepository repository) { }

    private Setup open(ProjectIndexes indexes) {
        AgentConfig discussionConfig = new AgentConfig(DISCUSSION, "context", "", Set.of(), List.of(), Set.of(MAIL), false);
        LuceneContextRetriever retriever = new LuceneContextRetriever(indexes, SOURCES,
                id -> DISCUSSION.equals(id) ? Optional.of(discussionConfig) : Optional.empty());
        TopicContextAgent discussion = new TopicContextAgent(new TopicDefinition(TopicType.DISCUSSION, DISCUSSION,
                PROJECT, "", List.of(), Set.of(MAIL), 10_000), retriever);
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(discussion);
        registry.register(reply);
        IndexConversationRepository repository = new IndexConversationRepository(indexes,
                project -> Optional.ofNullable(SOURCES.get(MAIL)).filter(source -> source.projectId().equals(project)));
        ConversationRecorder recorder = new ConversationRecorder(repository, MessageTagger.none());
        Clock clock = Clock.fixed(AT, ZoneOffset.UTC);
        ScriptedCoordinator coordinator = new ScriptedCoordinator(MetadataAgent.rulesOnly(),
                new InMemoryBlackboardFactory(clock), registry, new ScoreRoutingStrategy(0.5, 0.1),
                new ContextFirstCoordinationStrategy(List.of(discussion), new TopicCandidateFilter(3), Optional.empty(),
                        new DraftFirstCoordinationStrategy(), 2), clock, Optional.of(recorder));
        return new Setup(indexes, coordinator, repository);
    }

    private static BlackboardSnapshot handle(ScriptedCoordinator coordinator, String id, String text, int minute) {
        BoardId board = coordinator.receive(new Email(id, THREAD, "student@example.org", List.of("me@example.org"),
                "Room booking", text, AT.plusSeconds(60L * minute), Map.of()));
        return coordinator.run(board, new ExecutionLimits(20, 0, 1, 100_000, 100_000));
    }

    private static List<String> found(BlackboardSnapshot state) {
        return state.evidence().stream().filter(TopicEvidence::isTopicEvidence)
                .map(item -> TopicEvidence.documentId(item.id())).toList();
    }

    @Test
    void theSecondEmailSeesTheFirstButNotItselfOrTheUnsentDraft() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            Setup setup = open(indexes);
            BlackboardSnapshot first = handle(setup.coordinator(), "<one@x>", "Can I book room 12 on Monday?", 0);
            assertEquals(BoardStatus.AWAITING_REVIEW, first.status());
            assertTrue(found(first).isEmpty(), "the first email has no history and does not find itself");

            BlackboardSnapshot second = handle(setup.coordinator(), "<two@x>", "Sorry, I meant Tuesday.", 1);

            assertEquals(List.of("<one@x>"), found(second));
            assertFalse(second.evidence().stream().anyMatch(item -> item.text().contains("unsent draft words")));
            assertEquals(Set.of(MessageKind.RECEIVED, MessageKind.GENERATED_DRAFT), Set.copyOf(setup.repository()
                    .find(THREAD, new HistoryQuery(Set.of(MessageKind.values()), 10)).stream()
                    .map(message -> message.kind()).toList()));
        }
    }

    @Test
    void historySurvivesARestart() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(folder)) {
            handle(open(indexes).coordinator(), "<one@x>", "Can I book room 12 on Monday?", 0);
        }
        try (ProjectIndexes reopened = ProjectIndexes.onDisk(folder)) {
            BlackboardSnapshot second = handle(open(reopened).coordinator(), "<two@x>", "Sorry, I meant Tuesday.", 1);
            assertEquals(List.of("<one@x>"), found(second));
        }
    }

    @Test
    void aStorageFailureIsRecordedAndTheEmailIsStillHandled() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            Setup setup = open(indexes);
            BoardId board = setup.coordinator().receive(new Email("<x@y>", new Scope("no-mail-source", "t"),
                    "a@b.c", List.of(), "s", "text", AT, Map.of()));
            BlackboardSnapshot state = setup.coordinator().run(board, new ExecutionLimits(20, 0, 1, 100_000, 100_000));
            assertEquals(BoardStatus.AWAITING_REVIEW, state.status());
            assertTrue(state.executions().stream().anyMatch(record -> record.status() == TaskStatus.FAILED
                    && record.summary().startsWith("History not stored")));
        }
    }
}
