package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.supermas.prototype.Fixtures.agent;
import static org.supermas.prototype.Fixtures.email;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.Match;
import org.supermas.model.Domain.NextAction;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/** The coordinator stand-in on the durable SQLite blackboard: leases, failures, promotion and restarts. */
class LeasedCoordinatorTest {
    private static final Clock CLOCK = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);
    private static final Scope SCOPE = new Scope("project", "thread");
    private static final ExecutionLimits LIMITS = new ExecutionLimits(6, 1, 2, 1000, 1000);
    private static final String CONTEXT = "ctx";

    @TempDir
    Path data;

    private final InMemoryAgentRegistry registry = new InMemoryAgentRegistry();

    private LeasedCoordinator coordinator(CoordinationStrategy strategy) {
        SqliteBlackboardStore store = new SqliteBlackboardStore(data.resolve("blackboard.db"), CLOCK);
        return new LeasedCoordinator(MetadataAgent.rulesOnly(), new DefaultBlackboardFactory(store), registry,
                new ScoreRoutingStrategy(0.5, 0.1), strategy, EvidencePromotion.inferencesOnly(), Optional.empty(),
                Duration.ofMinutes(1));
    }

    /** Runs the context agent once, then routes and drafts. */
    private static CoordinationStrategy contextThenDraft() {
        DraftFirstCoordinationStrategy draft = new DraftFirstCoordinationStrategy();
        AtomicInteger calls = new AtomicInteger();
        return (board, limits) -> calls.getAndIncrement() == 0
                ? new NextAction(Action.RETRIEVE_CONTEXT, Optional.of(CONTEXT), "context first")
                : draft.next(board, limits);
    }

    private static SpecializedAgent writer(String id, Set<String> sources) {
        AgentConfig config = agent(id, Set.of(Capabilities.DRAFT_REPLY), sources, false);
        return new SpecializedAgent() {
            @Override
            public AgentConfig config() {
                return config;
            }

            @Override
            public AgentAssessment assess(Task task, BlackboardView board) {
                return new AgentAssessment(id, id.equals("student") ? 0.9 : 0.1, true, "fixed");
            }

            @Override
            public AgentOutcome execute(Task task, AgentBlackboard board) {
                board.submitDraft(new DraftProposal("Re: Subject", "Reply", id, "why"));
                return new AgentOutcome(TaskStatus.SUCCEEDED, "drafted", List.of(), Optional.empty());
            }
        };
    }

    /** A context agent whose work is the given function of its board access and the task. */
    private static SpecializedAgent context(BiFunction<AgentBlackboard, Task, AgentOutcome> work) {
        AgentConfig config = agent(CONTEXT, Set.of(), Set.of(CONTEXT), false);
        return new SpecializedAgent() {
            @Override
            public AgentConfig config() {
                return config;
            }

            @Override
            public AgentAssessment assess(Task task, BlackboardView board) {
                return new AgentAssessment(CONTEXT, 1, true, "context");
            }

            @Override
            public AgentOutcome execute(Task task, AgentBlackboard board) {
                return work.apply(board, task);
            }
        };
    }

    private static Evidence inference(Set<String> readers) {
        return new Evidence("note", SCOPE, EvidenceKind.MODEL_INFERENCE, new Provenance(CONTEXT, "test", Fixtures.NOW),
                "Possibly missing: DATE", readers);
    }

    private static final AgentOutcome DONE = new AgentOutcome(TaskStatus.SUCCEEDED, "done", List.of(), Optional.empty());

    @Test
    void aFullLoopLeasesEveryAssessmentAndTheDraft() {
        registry.register(writer("student", Set.of()));
        registry.register(writer("teacher", Set.of()));
        LeasedCoordinator coordinator = coordinator(new DraftFirstCoordinationStrategy());

        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Could you send me the form?")), LIMITS);

        assertEquals(BoardStatus.AWAITING_REVIEW, done.status());
        assertEquals(Match.CLEAR, done.routing().orElseThrow().match());
        assertEquals("student", done.drafts().get(0).agentId());
        assertEquals(2, done.budgetUsage().rounds(), "routing is one round however many agents assess");
        // Two assessments and one generation, each a leased task with its own execution record from the board.
        assertEquals(3, done.executions().size());
        assertTrue(done.tasks().stream().allMatch(task -> task.status() == TaskStatus.SUCCEEDED), done.tasks().toString());
    }

    @Test
    void aContextInferenceIsPromotedOnlyToAgentsListingItsSource() {
        registry.register(context((board, task) -> {
            board.submitEvidence(List.of(inference(Set.of(CONTEXT))));
            return DONE;
        }));
        registry.register(writer("student", Set.of(CONTEXT)));
        registry.register(writer("teacher", Set.of()));
        LeasedCoordinator coordinator = coordinator(contextThenDraft());

        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);

        assertEquals(BoardStatus.AWAITING_REVIEW, done.status());
        Evidence copy = done.evidence().stream().filter(item -> item.id().equals("note" + EvidencePromotion.SUFFIX))
                .findFirst().orElseThrow();
        assertEquals(Set.of("student"), copy.permittedAgentIds());
        assertEquals(Set.of(CONTEXT), done.evidence().stream().filter(item -> item.id().equals("note")).findFirst()
                .orElseThrow().permittedAgentIds());
    }

    @Test
    void aContributionVisibleToOthersIsRejectedByTheBoardAndFailsTheTask() {
        registry.register(context((board, task) -> {
            board.submitEvidence(List.of(inference(Set.of(CONTEXT, "student"))));
            return DONE;
        }));
        registry.register(writer("student", Set.of(CONTEXT)));
        LeasedCoordinator coordinator = coordinator(contextThenDraft());

        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);

        assertEquals(BoardStatus.FAILED, done.status());
        assertEquals(FailureKind.AGENT, done.failure().orElseThrow().kind());
        assertTrue(done.evidence().isEmpty());
        assertEquals(TaskStatus.FAILED, done.executions().get(0).status());
    }

    @Test
    void nothingIsPromotedFromAFailedTask() {
        registry.register(context((board, task) -> {
            board.submitEvidence(List.of(inference(Set.of(CONTEXT))));
            return new AgentOutcome(TaskStatus.FAILED, "half done", List.of(),
                    Optional.of(new Failure(FailureKind.CONTEXT, "index unreadable", false)));
        }));
        registry.register(writer("student", Set.of(CONTEXT)));
        LeasedCoordinator coordinator = coordinator(contextThenDraft());

        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);

        assertEquals(BoardStatus.FAILED, done.status());
        assertTrue(done.evidence().stream().noneMatch(item -> item.id().endsWith(EvidencePromotion.SUFFIX)));
    }

    @Test
    void anAgentThatThrowsBecomesARecordedFailureNotACrash() {
        registry.register(context((board, task) -> {
            throw new IllegalStateException("boom");
        }));
        LeasedCoordinator coordinator = coordinator(contextThenDraft());

        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);

        assertEquals(BoardStatus.FAILED, done.status());
        assertTrue(done.failure().orElseThrow().message().contains("boom"));
        assertEquals(TaskStatus.FAILED, done.tasks().get(0).status());
    }

    @Test
    void aSuggestedTaskKeepsItsInformationNeedForTheAgentThatRunsIt() {
        AtomicInteger runs = new AtomicInteger();
        List<Task> seen = new java.util.ArrayList<>();
        registry.register(context((board, task) -> {
            seen.add(task);
            if (runs.getAndIncrement() > 0) {
                return DONE;
            }
            Task further = new Task("further", task.boardId(), Action.RETRIEVE_CONTEXT, Optional.empty(),
                    TaskStatus.PENDING, "more", Optional.of(new org.supermas.model.Domain.InformationNeed(Set.of("DATE"),
                    List.of("calendar"))));
            return new AgentOutcome(TaskStatus.SUCCEEDED, "suggested", List.of(further), Optional.empty());
        }));
        AtomicInteger calls = new AtomicInteger();
        CoordinationStrategy twice = (board, limits) -> calls.getAndIncrement() < 2
                ? new NextAction(Action.RETRIEVE_CONTEXT, Optional.of(CONTEXT), "again")
                : new NextAction(Action.STOP, Optional.empty(), "enough");
        LeasedCoordinator coordinator = coordinator(twice);

        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);

        assertEquals(BoardStatus.STOPPED, done.status());
        assertEquals("further", seen.get(1).id());
        assertEquals(List.of("calendar"), seen.get(1).need().orElseThrow().searchTerms());
    }

    @Test
    void theRoundLimitStopsAStrategyThatNeverEnds() {
        registry.register(context((board, task) -> DONE));
        CoordinationStrategy forever = (board, limits) -> new NextAction(Action.RETRIEVE_CONTEXT, Optional.of(CONTEXT), "again");
        LeasedCoordinator coordinator = coordinator(forever);

        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);

        assertEquals(BoardStatus.FAILED, done.status());
        assertEquals(FailureKind.BUDGET_EXHAUSTED, done.failure().orElseThrow().kind());
        assertEquals(LIMITS.maxRounds(), done.budgetUsage().rounds());
    }

    @Test
    void aBoardIsReadAgainAfterARestartAndAStoppedBoardDoesNoMoreWork() {
        registry.register(writer("student", Set.of()));
        BoardId id = coordinator(new DraftFirstCoordinationStrategy()).receive(email(SCOPE, "Hello"));

        LeasedCoordinator restarted = coordinator(new DraftFirstCoordinationStrategy());
        assertEquals(BoardStatus.PROCESSING, restarted.inspect(id).status());
        restarted.stop(id, "user request");
        BlackboardSnapshot done = restarted.run(id, LIMITS);

        assertEquals(BoardStatus.STOPPED, done.status());
        assertTrue(done.drafts().isEmpty());
        assertThrows(RuntimeException.class, () -> restarted.inspect(new BoardId(SCOPE, "unknown")));
    }
}
