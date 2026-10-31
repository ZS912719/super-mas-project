package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.supermas.prototype.Fixtures.agent;
import static org.supermas.prototype.Fixtures.email;
import static org.supermas.prototype.Fixtures.idleAgent;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.AgentBlackboard;
import org.supermas.blackboard.BlackboardView;
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
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.Match;
import org.supermas.model.Domain.NextAction;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.model.Domain.Scope;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

class ScriptedCoordinatorTest {
    private static final Clock CLOCK = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);
    private static final Scope SCOPE = new Scope("project", "thread");
    private static final ExecutionLimits LIMITS = new ExecutionLimits(5, 1, 2, 1000, 1000);

    private final InMemoryAgentRegistry registry = new InMemoryAgentRegistry();

    private ScriptedCoordinator coordinator(CoordinationStrategy strategy) {
        return new ScriptedCoordinator(MetadataAgent.rulesOnly(), new InMemoryBlackboardFactory(CLOCK), registry,
                new ScoreRoutingStrategy(0.5, 0.1), strategy, CLOCK);
    }

    private ScriptedCoordinator coordinator() {
        return coordinator(new DraftFirstCoordinationStrategy());
    }

    /** Fails with the given failure for its first attempts, then drafts a reply. */
    private static SpecializedAgent writer(String id, double score, boolean fallback, int failures, Failure failure) {
        AgentConfig config = agent(id, Set.of(Capabilities.DRAFT_REPLY), Set.of(), fallback);
        AtomicInteger attempts = new AtomicInteger();
        return new SpecializedAgent() {
            @Override
            public AgentConfig config() {
                return config;
            }

            @Override
            public AgentAssessment assess(Task task, BlackboardView board) {
                return new AgentAssessment(id, score, true, "fixed");
            }

            @Override
            public AgentOutcome execute(Task task, AgentBlackboard board) {
                if (attempts.getAndIncrement() < failures) {
                    return new AgentOutcome(TaskStatus.FAILED, "failed", List.of(), Optional.of(failure));
                }
                board.submitDraft(new DraftProposal("Re: Subject", "Reply", id, "why"));
                return new AgentOutcome(TaskStatus.SUCCEEDED, "drafted", List.of(), Optional.empty());
            }
        };
    }

    private static SpecializedAgent writer(String id, double score, boolean fallback) {
        return writer(id, score, fallback, 0, null);
    }

    @Test
    void aFakeAgentCompletesAFullLoopWithinBudget() {
        registry.register(writer("student", 0.9, false));
        ScriptedCoordinator coordinator = coordinator();
        BoardId id = coordinator.receive(email(SCOPE, "Could you send me the form?"));
        assertTrue(coordinator.inspect(id).analysis().initialTags().contains("act:request"));

        BlackboardSnapshot done = coordinator.run(id, LIMITS);

        assertEquals(BoardStatus.AWAITING_REVIEW, done.status());
        assertEquals(Match.CLEAR, done.routing().orElseThrow().match());
        assertEquals("student", done.drafts().get(0).agentId());
        assertEquals(ReviewStatus.PROPOSED, done.drafts().get(0).review());
        assertEquals(2, done.budgetUsage().rounds());
        assertEquals(1, done.budgetUsage().generationAttempts());
        assertEquals(2, done.executions().size());
        assertTrue(done.failure().isEmpty());
    }

    @Test
    void anAmbiguousOrAbsentMatchGoesToTheFallbackAndKeepsTheCandidates() {
        registry.register(writer("a", 0.8, false));
        registry.register(writer("b", 0.75, false));
        registry.register(writer("generic", 0.0, true));
        ScriptedCoordinator coordinator = coordinator();
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);
        assertEquals(Match.AMBIGUOUS, done.routing().orElseThrow().match());
        assertEquals(3, done.routing().orElseThrow().candidates().size());
        assertEquals("generic", done.drafts().get(0).agentId());
    }

    @Test
    void contextAgentsAreNeverAssessedAsReplyCandidates() {
        registry.register(idleAgent(agent("context", Set.of("retrieve"), Set.of(), false), 1.0));
        registry.register(writer("student", 0.9, false));
        ScriptedCoordinator coordinator = coordinator();
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);
        assertEquals(List.of("student"),
                done.routing().orElseThrow().candidates().stream().map(AgentAssessment::agentId).toList());
    }

    @Test
    void theRoundLimitStopsAStrategyThatNeverEnds() {
        registry.register(idleAgent(agent("context", Set.of("retrieve"), Set.of(), false), 0));
        CoordinationStrategy forever = (board, limits) ->
                new NextAction(Action.RETRIEVE_CONTEXT, Optional.of("context"), "again");
        ScriptedCoordinator coordinator = coordinator(forever);
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);
        assertEquals(BoardStatus.FAILED, done.status());
        assertEquals(FailureKind.BUDGET_EXHAUSTED, done.failure().orElseThrow().kind());
        assertEquals(LIMITS.maxRounds(), done.budgetUsage().rounds());
        assertEquals(LIMITS.maxRounds(), done.executions().size());
    }

    @Test
    void aRetryableFailureIsRetriedWithinBudgetAndKeptInTheHistory() {
        Failure timeout = new Failure(FailureKind.TIMEOUT, "slow", true);
        registry.register(writer("student", 0.9, false, 1, timeout));
        ScriptedCoordinator coordinator = coordinator();
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);
        assertEquals(BoardStatus.AWAITING_REVIEW, done.status());
        assertEquals(1, done.budgetUsage().retries());
        assertEquals(2, done.budgetUsage().generationAttempts());
        assertEquals(FailureKind.TIMEOUT, done.executions().get(1).failure().orElseThrow().kind());
        assertTrue(done.failure().isEmpty());
    }

    @Test
    void retriesEndAtTheRetryLimit() {
        Failure timeout = new Failure(FailureKind.TIMEOUT, "slow", true);
        registry.register(writer("student", 0.9, false, 99, timeout));
        ScriptedCoordinator coordinator = coordinator();
        ExecutionLimits generous = new ExecutionLimits(20, 2, 20, 1000, 1000);
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), generous);
        assertEquals(BoardStatus.FAILED, done.status());
        assertEquals(FailureKind.TIMEOUT, done.failure().orElseThrow().kind());
        assertEquals(2, done.budgetUsage().retries());
        assertEquals(3, done.budgetUsage().generationAttempts());
    }

    @Test
    void theGenerationLimitEndsTheLoopBeforeAnotherAttempt() {
        Failure timeout = new Failure(FailureKind.TIMEOUT, "slow", true);
        registry.register(writer("student", 0.9, false, 99, timeout));
        ScriptedCoordinator coordinator = coordinator();
        ExecutionLimits tight = new ExecutionLimits(20, 20, 2, 1000, 1000);
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), tight);
        assertEquals(FailureKind.BUDGET_EXHAUSTED, done.failure().orElseThrow().kind());
        assertEquals(2, done.budgetUsage().generationAttempts());
    }

    @Test
    void anAgentBreakingARuleBecomesARecordedFailure() {
        AgentConfig noDrafting = agent("sneaky", Set.of(), Set.of(), true);
        registry.register(new SpecializedAgent() {
            @Override
            public AgentConfig config() {
                return noDrafting;
            }

            @Override
            public AgentAssessment assess(Task task, BlackboardView board) {
                return new AgentAssessment("sneaky", 1, true, "");
            }

            @Override
            public AgentOutcome execute(Task task, AgentBlackboard board) {
                board.submitDraft(new DraftProposal("s", "b", "sneaky", "e"));
                return new AgentOutcome(TaskStatus.SUCCEEDED, "done", List.of(), Optional.empty());
            }
        });
        ScriptedCoordinator coordinator = coordinator();
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);
        assertEquals(BoardStatus.FAILED, done.status());
        assertEquals(FailureKind.AGENT, done.failure().orElseThrow().kind());
        assertTrue(done.drafts().isEmpty());
    }

    @Test
    void noAvailableAgentIsAFailureNotACrash() {
        ScriptedCoordinator coordinator = coordinator();
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);
        assertEquals(Match.NONE, done.routing().orElseThrow().match());
        assertEquals(BoardStatus.FAILED, done.status());
    }

    @Test
    void aFinishedBoardDoesNoFurtherWorkOnALaterRun() {
        registry.register(idleAgent(agent("context", Set.of(), Set.of(), false), 0));
        AtomicInteger calls = new AtomicInteger();
        CoordinationStrategy once = (board, limits) -> calls.getAndIncrement() < 2
                ? new NextAction(Action.RETRIEVE_CONTEXT, Optional.of("context"), "again")
                : new NextAction(Action.STOP, Optional.empty(), "enough");
        ScriptedCoordinator coordinator = coordinator(once);
        BoardId id = coordinator.receive(email(SCOPE, "Hello"));
        BlackboardSnapshot first = coordinator.run(id, LIMITS);
        assertEquals(BoardStatus.STOPPED, first.status());
        assertEquals(2, first.budgetUsage().rounds());
        assertEquals(first, coordinator.run(id, LIMITS));
    }

    @Test
    void stoppedBoardsDoNoMoreWorkAndUnknownBoardsAreRejected() {
        registry.register(writer("student", 0.9, false));
        ScriptedCoordinator coordinator = coordinator();
        BoardId id = coordinator.receive(email(SCOPE, "Hello"));
        coordinator.stop(id, "user request");
        BlackboardSnapshot done = coordinator.run(id, LIMITS);
        assertEquals(BoardStatus.STOPPED, done.status());
        assertTrue(done.drafts().isEmpty());
        assertEquals("Stopped: user request", done.executions().get(0).summary());
        BoardId unknown = new BoardId(SCOPE, "unknown");
        assertThrows(java.util.NoSuchElementException.class, () -> coordinator.inspect(unknown));
    }

    @Test
    void presentingWithoutADraftFails() {
        CoordinationStrategy wrong = (board, limits) -> new NextAction(Action.PRESENT_DRAFT, Optional.empty(), "x");
        ScriptedCoordinator coordinator = coordinator(wrong);
        BlackboardSnapshot done = coordinator.run(coordinator.receive(email(SCOPE, "Hello")), LIMITS);
        assertEquals(BoardStatus.FAILED, done.status());
    }
}
