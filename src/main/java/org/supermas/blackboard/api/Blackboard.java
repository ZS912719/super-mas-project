package org.supermas.blackboard.api;

import java.util.List;

import org.supermas.model.Domain.*;

/** Coordinator-facing control access to one isolated task blackboard. */
public interface Blackboard extends BlackboardView {
    AgentBlackboard accessFor(AgentConfig agent);
    void recordRouting(RoutingDecision decision);
    void addEvidence(List<Evidence> evidence);
    void enqueue(Task task);
    void updateTask(Task task);
    void recordExecution(ExecutionRecord execution);
    void recordBudgetUsage(BudgetUsage usage);
    void setStatus(BoardStatus status);
    void recordFailure(Failure failure);
}
