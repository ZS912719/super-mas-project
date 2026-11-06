package org.supermas.prototype;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.Preprocessor;
import org.supermas.blackboard.impl.DefaultBlackboard;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.blackboard.model.BlackboardData.RecoveryPolicy;
import org.supermas.blackboard.model.BlackboardData.TaskLease;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.coordinator.Coordinator;
import org.supermas.coordinator.RoutingStrategy;
import org.supermas.memory.ConversationRecorder;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.BudgetUsage;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.NextAction;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

// PROTOTYPE-STAND-IN: owned by the team's coordinator work, not by the Metadata/Context agents.
// Required by these agents: preprocessing on receive, one action per round, agents reached only through
// restricted board access, enforced budgets, and promotion of context agents' findings to their readers.
// It runs on the durable blackboard of codex/blackboard-storage without changing it: every agent action is a
// pending task that the agent claims under a lease, works on outside any database transaction, and completes.
// Replace with the real implementation.
/**
 * The {@link ScriptedCoordinator} loop on {@link DefaultBlackboard}. Differences, all forced by that board:
 *
 * <ul>
 *   <li>Each assessment during routing is its own leased task, because the board gives an agent a restricted
 *       view only through a lease.</li>
 *   <li>Execution records come from the board's {@code complete}; the coordinator cannot write them.</li>
 *   <li>After a context agent's task succeeds, its private findings are promoted by {@link EvidencePromotion}.
 *       A reply agent's own evidence stays private. Nothing is promoted from a failed task.</li>
 *   <li>A history failure is recorded with {@code recordFailure}, without stopping the email, because the
 *       board has no other place for a coordinator's own note.</li>
 *   <li>Boards are reopened from the store, so {@link #inspect} works after a restart.</li>
 * </ul>
 */
public final class LeasedCoordinator implements Coordinator {
    private final Preprocessor preprocessor;
    private final DefaultBlackboardFactory factory;
    private final InMemoryAgentRegistry registry;
    private final RoutingStrategy routing;
    private final CoordinationStrategy strategy;
    private final EvidencePromotion promotion;
    private final Optional<ConversationRecorder> recorder;
    private final Duration lease;

    public LeasedCoordinator(Preprocessor preprocessor, DefaultBlackboardFactory factory, InMemoryAgentRegistry registry,
                             RoutingStrategy routing, CoordinationStrategy strategy, EvidencePromotion promotion,
                             Optional<ConversationRecorder> recorder, Duration lease) {
        this.preprocessor = preprocessor;
        this.factory = factory;
        this.registry = registry;
        this.routing = routing;
        this.strategy = strategy;
        this.promotion = promotion;
        this.recorder = recorder;
        this.lease = lease;
    }

    @Override
    public BoardId receive(Email email) {
        Analysis analysis = preprocessor.analyze(email);
        DefaultBlackboard board = factory.create(email, analysis);
        // ASSUMED-TEAM-DECISION(P10): the coordinator records each received email. Pending team validation.
        recorder.ifPresent(found -> history(board, () -> found.onReceived(email, analysis)));
        return board.snapshot().id();
    }

    @Override
    public BlackboardSnapshot run(BoardId boardId, ExecutionLimits limits) {
        DefaultBlackboard board = factory.open(boardId);
        while (board.snapshot().status() == BoardStatus.PROCESSING) {
            step(board, limits);
        }
        return board.snapshot();
    }

    private void step(DefaultBlackboard board, ExecutionLimits limits) {
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
        board.recordBudgetUsage(new BudgetUsage(used.rounds() + 1, used.retries(),
                used.generationAttempts() + (generates ? 1 : 0)));
        if (next.action() == Action.ROUTE || next.action() == Action.RECLASSIFY) {
            route(board);
        } else {
            execute(board, next, limits);
        }
    }

    /** Every reply candidate assesses the email in its own leased task, through its own restricted view. */
    private void route(DefaultBlackboard board) {
        String round = roundId(board.snapshot());
        List<AgentAssessment> candidates = new ArrayList<>();
        for (SpecializedAgent agent : registry.replyCandidates()) {
            String id = agent.config().id();
            Task task = new Task(round + "-assess-" + id, board.snapshot().id(), Action.ROUTE, Optional.of(id),
                    TaskStatus.PENDING, "Assess fit for routing");
            AgentAssessment assessment;
            try {
                board.enqueue(task);
                TaskLease claimed = board.claim(task.id(), agent.config(), lease, RecoveryPolicy.RETRY_SAFE);
                try {
                    assessment = agent.assess(task, board.accessFor(claimed));
                    board.complete(claimed, TaskStatus.SUCCEEDED, "Score " + assessment.score());
                } catch (RuntimeException e) {
                    assessment = new AgentAssessment(id, 0, false, "Assessment failed: " + e.getMessage());
                    board.complete(claimed, TaskStatus.FAILED, assessment.reason());
                }
            } catch (RuntimeException e) {
                assessment = new AgentAssessment(id, 0, false, "Assessment failed: " + e.getMessage());
            }
            candidates.add(assessment);
        }
        board.recordRouting(routing.select(board, candidates));
    }

    private void execute(DefaultBlackboard board, NextAction next, ExecutionLimits limits) {
        BlackboardSnapshot before = board.snapshot();
        Optional<SpecializedAgent> agent = next.agentId().isPresent()
                ? registry.find(next.agentId().get())
                : registry.fallback();
        if (agent.isEmpty()) {
            fail(board, new Failure(FailureKind.AGENT, "No agent available for " + next.action(), false));
            return;
        }
        AgentConfig config = agent.get().config();
        // A pending suggestion for this action is carried out, with its information need, instead of a new task.
        Optional<Task> pending = before.tasks().stream()
                .filter(item -> item.status() == TaskStatus.PENDING && item.action() == next.action()
                        && (item.agentId().isEmpty() || item.agentId().equals(Optional.of(config.id()))))
                .findFirst();
        Task task = pending.orElseGet(() -> new Task(roundId(before), before.id(), next.action(),
                Optional.of(config.id()), TaskStatus.PENDING, next.reason()));
        Failure failure = null;
        try {
            if (pending.isEmpty()) {
                board.enqueue(task);
            }
            TaskLease claimed = board.claim(task.id(), config, lease, RecoveryPolicy.RETRY_SAFE);
            String summary;
            try {
                // The original task is handed over: the board keeps a claimed task without its information need.
                AgentBlackboard access = board.accessFor(claimed);
                AgentOutcome outcome = agent.get().execute(task, access);
                if (!outcome.proposedTasks().isEmpty()) {
                    access.proposeTasks(outcome.proposedTasks());
                }
                summary = outcome.summary();
                if (outcome.status() != TaskStatus.SUCCEEDED) {
                    failure = outcome.failure()
                            .orElse(new Failure(FailureKind.AGENT, "The agent reported " + outcome.status(), false));
                }
            } catch (RuntimeException e) {
                failure = new Failure(FailureKind.AGENT, "The agent failed: " + e.getMessage(), false);
                summary = failure.message();
            }
            board.complete(claimed, failure == null ? TaskStatus.SUCCEEDED : TaskStatus.FAILED, summary);
        } catch (RuntimeException e) {
            failure = new Failure(FailureKind.AGENT, "The task could not run: " + e.getMessage(), false);
        }
        if (failure == null && isContextAgent(config)) {
            promote(board, config, before);
        }
        // ASSUMED-TEAM-DECISION(P10): new drafts are recorded as history. Pending team validation.
        List<Draft> drafts = board.snapshot().drafts();
        if (recorder.isPresent() && drafts.size() > before.drafts().size()) {
            BoardId id = before.id();
            for (Draft draft : drafts.subList(before.drafts().size(), drafts.size())) {
                history(board, () -> recorder.get().onDraft(id, draft));
            }
        }
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

    private void promote(DefaultBlackboard board, AgentConfig author, BlackboardSnapshot before) {
        Set<String> earlier = new HashSet<>();
        before.evidence().forEach(item -> earlier.add(item.id()));
        List<Evidence> added = board.snapshot().evidence().stream().filter(item -> !earlier.contains(item.id())).toList();
        List<AgentConfig> agents = registry.all().stream().map(SpecializedAgent::config).toList();
        List<Evidence> copies = promotion.promote(author, added, agents);
        if (!copies.isEmpty()) {
            board.addEvidence(copies);
        }
    }

    /** Context agents are the registered agents that are not reply candidates. */
    private boolean isContextAgent(AgentConfig config) {
        return registry.replyCandidates().stream().noneMatch(agent -> agent.config().id().equals(config.id()));
    }

    /** History is kept beside the board, so a storage failure is noted and processing goes on. */
    private static void history(DefaultBlackboard board, Supplier<?> store) {
        try {
            store.get();
        } catch (RuntimeException e) {
            board.recordFailure(new Failure(FailureKind.STORAGE, "History not stored: " + e.getMessage(), true));
        }
    }

    private static void fail(DefaultBlackboard board, Failure failure) {
        board.recordFailure(failure);
        board.setStatus(BoardStatus.FAILED);
    }

    /** Unique on a board, because rounds only ever grow. */
    private static String roundId(BlackboardSnapshot state) {
        return state.id().taskId() + "-round-" + state.budgetUsage().rounds();
    }

    @Override
    public BlackboardSnapshot inspect(BoardId boardId) {
        return factory.open(boardId).snapshot();
    }

    /** Pending work is cancelled, then the board stops. The reason is not stored: the board has no field for it. */
    @Override
    public void stop(BoardId boardId, String reason) {
        DefaultBlackboard board = factory.open(boardId);
        for (Task task : board.snapshot().tasks()) {
            if (task.status() == TaskStatus.PENDING) {
                board.updateTask(new Task(task.id(), task.boardId(), task.action(), task.agentId(),
                        TaskStatus.CANCELLED, task.reason(), task.need()));
            }
        }
        board.setStatus(BoardStatus.STOPPED);
    }
}
