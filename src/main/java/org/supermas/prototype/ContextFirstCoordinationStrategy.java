package org.supermas.prototype;

import java.util.List;
import java.util.Optional;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.context.TopicCandidateFilter;
import org.supermas.context.TopicContextAgent;
import org.supermas.context.TopicType;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.NextAction;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

// PROTOTYPE-STAND-IN: owned by the team's scheduling work, not by the Metadata/Context agents.
// Required by these agents: each selected context agent runs once before routing, the missing-information
// check runs after them, and one pending further retrieval is carried out, all within the round budget.
// Replace with the real implementation.
/** Context first, then whatever comes next. Rounds are kept back so that context can never use up the reply's budget. */
public final class ContextFirstCoordinationStrategy implements CoordinationStrategy {
    private final List<TopicContextAgent> topics;
    private final List<SpecializedAgent> others;
    private final TopicCandidateFilter filter;
    private final Optional<String> checkAgentId;
    private final CoordinationStrategy then;
    private final int reservedRounds;

    /**
     * @param checkAgentId the missing-information agent, or empty for no check
     * @param then what decides once context is done
     * @param reservedRounds rounds kept for what follows
     */
    public ContextFirstCoordinationStrategy(List<TopicContextAgent> topics, TopicCandidateFilter filter,
                                            Optional<String> checkAgentId, CoordinationStrategy then, int reservedRounds) {
        this(topics, List.of(), filter, checkAgentId, then, reservedRounds);
    }

    /**
     * @param others further context agents, such as the discovered-topics agent. Each runs once, after the topic
     *               candidates, when its assessment score is above zero
     */
    public ContextFirstCoordinationStrategy(List<TopicContextAgent> topics, List<SpecializedAgent> others,
                                            TopicCandidateFilter filter, Optional<String> checkAgentId,
                                            CoordinationStrategy then, int reservedRounds) {
        this.others = List.copyOf(others);
        this.topics = List.copyOf(topics);
        this.filter = filter;
        this.checkAgentId = checkAgentId;
        this.then = then;
        this.reservedRounds = reservedRounds;
    }

    @Override
    public NextAction next(BlackboardView board, ExecutionLimits limits) {
        BlackboardSnapshot state = board.snapshot();
        boolean past = state.routing().isPresent() || !state.drafts().isEmpty();
        boolean budget = limits.maxRounds() - state.budgetUsage().rounds() > reservedRounds;
        if (past || !budget) {
            return then.next(board, limits);
        }
        List<TopicContextAgent> candidates = filter.select(topics, board);
        for (TopicContextAgent agent : candidates) {
            if (!hasRun(state, agent.config().id())) {
                return retrieve(agent.config().id(), "Topic candidate that has not run");
            }
        }
        List<SpecializedAgent> fitting = others.stream().filter(agent -> fits(agent, board)).toList();
        for (SpecializedAgent agent : fitting) {
            if (!hasRun(state, agent.config().id())) {
                return retrieve(agent.config().id(), "Context agent that fits and has not run");
            }
        }
        if (checkAgentId.isPresent() && !hasRun(state, checkAgentId.get())) {
            return retrieve(checkAgentId.get(), "Missing-information check");
        }
        boolean needPending = state.tasks().stream().anyMatch(task -> task.status() == TaskStatus.PENDING
                && task.action() == Action.RETRIEVE_CONTEXT && task.need().isPresent());
        if (needPending) {
            Optional<TopicContextAgent> subject = candidates.stream()
                    .filter(agent -> agent.topic().type() != TopicType.DISCUSSION).findFirst();
            if (subject.isPresent()) {
                return retrieve(subject.get().config().id(), "Further retrieval for a possibly missing field");
            }
            if (!fitting.isEmpty()) {
                return retrieve(fitting.get(0).config().id(), "Further retrieval for a possibly missing field");
            }
        }
        return then.next(board, limits);
    }

    /** A failing assessment counts as not fitting, so one faulty agent does not stop the loop. */
    private static boolean fits(SpecializedAgent agent, BlackboardView board) {
        try {
            Task task = new Task("context-first", board.snapshot().id(), Action.RETRIEVE_CONTEXT, Optional.empty(),
                    TaskStatus.PENDING, "Context selection");
            return agent.assess(task, board).score() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hasRun(BlackboardSnapshot state, String agentId) {
        for (Task task : state.tasks()) {
            if (task.action() == Action.RETRIEVE_CONTEXT && task.agentId().filter(agentId::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static NextAction retrieve(String agentId, String reason) {
        return new NextAction(Action.RETRIEVE_CONTEXT, Optional.of(agentId), reason);
    }
}
