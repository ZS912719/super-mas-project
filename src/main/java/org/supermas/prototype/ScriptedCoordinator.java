package org.supermas.prototype;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.AgentBlackboard;
import org.supermas.blackboard.Blackboard;
import org.supermas.blackboard.BlackboardFactory;
import org.supermas.blackboard.Preprocessor;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.coordinator.Coordinator;
import org.supermas.coordinator.RoutingStrategy;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.BudgetUsage;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.ExecutionRecord;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.NextAction;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

// PROTOTYPE-STAND-IN: owned by the team's coordinator work, not by the Metadata/Context agents.
// Required by these agents: preprocessing on receive, one action per round, agents reached only through
// restricted board access, and enforced cumulative budgets.
// Replace with the real implementation.
/**
 * A single-threaded loop that asks the strategy for one action at a time. A failure from an agent,
 * including a rejected contribution, is recorded on the board instead of escaping to the caller.
 */
public final class ScriptedCoordinator implements Coordinator {
    private final Preprocessor preprocessor;
    private final BlackboardFactory factory;
    private final InMemoryAgentRegistry registry;
    private final RoutingStrategy routing;
    private final CoordinationStrategy strategy;
    private final Clock clock;
    private final Map<BoardId, Blackboard> boards = new ConcurrentHashMap<>();

    public ScriptedCoordinator(Preprocessor preprocessor, BlackboardFactory factory, InMemoryAgentRegistry registry,
                               RoutingStrategy routing, CoordinationStrategy strategy, Clock clock) {
        this.preprocessor = preprocessor;
        this.factory = factory;
        this.registry = registry;
        this.routing = routing;
        this.strategy = strategy;
        this.clock = clock;
    }

    @Override
    public BoardId receive(Email email) {
        Blackboard board = factory.create(email, preprocessor.analyze(email));
        BoardId id = board.snapshot().id();
        boards.put(id, board);
        return id;
    }

    @Override
    public BlackboardSnapshot run(BoardId boardId, ExecutionLimits limits) {
        Blackboard board = board(boardId);
        while (board.snapshot().status() == BoardStatus.PROCESSING) {
            step(board, limits);
        }
        return board.snapshot();
    }

    private void step(Blackboard board, ExecutionLimits limits) {
        NextAction next;
        try {
            next = strategy.next(board, limits);
        } catch (RuntimeException e) {
            fail(board, new Failure(FailureKind.AGENT, "The strategy failed: " + e.getMessage(), false));
            return;
        }
        if (next.action() == Action.STOP) {
            board.setStatus(BoardStatus.STOPPED);
            return;
        }
        if (next.action() == Action.PRESENT_DRAFT) {
            if (board.snapshot().drafts().isEmpty()) {
                fail(board, new Failure(FailureKind.AGENT, "There is no draft to present", false));
            } else {
                board.setStatus(BoardStatus.AWAITING_REVIEW);
            }
            return;
        }
        BudgetUsage used = board.snapshot().budgetUsage();
        boolean generates = next.action() == Action.GENERATE || next.action() == Action.REVISE;
        if (used.rounds() >= limits.maxRounds()) {
            fail(board, new Failure(FailureKind.BUDGET_EXHAUSTED, "Round limit reached", false));
            return;
        }
        if (generates && used.generationAttempts() >= limits.maxGenerationAttempts()) {
            fail(board, new Failure(FailureKind.BUDGET_EXHAUSTED, "Generation limit reached", false));
            return;
        }
        // ASSUMED-TEAM-DECISION(P8): each action, including each context agent execution, counts as one round.
        // Pending team validation. If rejected: change this counting and its tests.
        board.recordBudgetUsage(new BudgetUsage(used.rounds() + 1, used.retries(),
                used.generationAttempts() + (generates ? 1 : 0)));
        if (next.action() == Action.ROUTE || next.action() == Action.RECLASSIFY) {
            route(board);
        } else {
            execute(board, next, limits);
        }
    }

    private void route(Blackboard board) {
        BlackboardSnapshot state = board.snapshot();
        Task task = new Task(taskId(state), state.id(), Action.ROUTE, Optional.empty(), TaskStatus.RUNNING, "Routing");
        board.enqueue(task);
        List<AgentAssessment> candidates = new ArrayList<>();
        for (SpecializedAgent agent : registry.replyCandidates()) {
            String id = agent.config().id();
            try {
                // A restricted view: an agent assessing its suitability sees only what it may read.
                candidates.add(agent.assess(task, board.accessFor(agent.config())));
            } catch (RuntimeException e) {
                candidates.add(new AgentAssessment(id, 0, false, "Assessment failed: " + e.getMessage()));
            }
        }
        board.recordRouting(routing.select(board, candidates));
        finish(board, task, "Routing", TaskStatus.SUCCEEDED, "Routing recorded", Optional.empty());
    }

    private void execute(Blackboard board, NextAction next, ExecutionLimits limits) {
        BlackboardSnapshot state = board.snapshot();
        Optional<SpecializedAgent> agent = next.agentId().isPresent()
                ? registry.find(next.agentId().get())
                : registry.fallback();
        Task task = new Task(taskId(state), state.id(), next.action(), next.agentId(), TaskStatus.RUNNING, next.reason());
        board.enqueue(task);
        Failure failure;
        String summary;
        if (agent.isEmpty()) {
            failure = new Failure(FailureKind.AGENT, "No agent available for " + next.action(), false);
            summary = failure.message();
        } else {
            try {
                AgentBlackboard access = board.accessFor(agent.get().config());
                AgentOutcome outcome = agent.get().execute(task, access);
                access.proposeTasks(outcome.proposedTasks());
                summary = outcome.summary();
                failure = outcome.status() == TaskStatus.SUCCEEDED ? null : outcome.failure()
                        .orElse(new Failure(FailureKind.AGENT, "The agent reported " + outcome.status(), false));
            } catch (RuntimeException e) {
                failure = new Failure(FailureKind.AGENT, "The agent failed: " + e.getMessage(), false);
                summary = failure.message();
            }
        }
        finish(board, task, next.reason(), failure == null ? TaskStatus.SUCCEEDED : TaskStatus.FAILED, summary,
                Optional.ofNullable(failure));
        if (failure == null) {
            return;
        }
        BudgetUsage used = board.snapshot().budgetUsage();
        if (failure.retryable() && used.retries() < limits.maxRetries()) {
            board.recordBudgetUsage(new BudgetUsage(used.rounds(), used.retries() + 1, used.generationAttempts()));
        } else {
            fail(board, failure);
        }
    }

    private void finish(Blackboard board, Task task, String reason, TaskStatus status, String summary,
                        Optional<Failure> failure) {
        board.updateTask(new Task(task.id(), task.boardId(), task.action(), task.agentId(), status, task.reason(), task.need()));
        Instant now = clock.instant();
        board.recordExecution(new ExecutionRecord(task.id(), reason, status, now, now, summary, failure));
    }

    private static void fail(Blackboard board, Failure failure) {
        board.recordFailure(failure);
        board.setStatus(BoardStatus.FAILED);
    }

    /** Unique on a board, because rounds only ever grow. */
    private static String taskId(BlackboardSnapshot state) {
        return state.id().taskId() + "-round-" + state.budgetUsage().rounds();
    }

    @Override
    public BlackboardSnapshot inspect(BoardId boardId) {
        return board(boardId).snapshot();
    }

    @Override
    public void stop(BoardId boardId, String reason) {
        Blackboard board = board(boardId);
        Instant now = clock.instant();
        board.recordExecution(new ExecutionRecord(boardId.taskId() + "-stop", reason, TaskStatus.CANCELLED, now, now,
                "Stopped: " + reason, Optional.empty()));
        board.setStatus(BoardStatus.STOPPED);
    }

    private Blackboard board(BoardId boardId) {
        Blackboard board = boards.get(boardId);
        if (board == null) {
            throw new NoSuchElementException("Unknown board " + boardId);
        }
        return board;
    }
}
