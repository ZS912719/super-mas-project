package org.supermas.blackboard.api;

import java.util.List;

import org.supermas.model.Domain.*;

/** Coordinator-facing control access to one isolated task blackboard. */
public interface Blackboard extends BlackboardView {
    AgentBlackboard accessFor(AgentConfig agent);

    /**
     * A restricted, read-only view for one agent, with the same filtering as its write access. It needs no
     * lease, because it changes nothing: the controller asks every knowledge source for an estimate through it
     * (prototype branch, step 12.1).
     */
    BlackboardView viewFor(AgentConfig agent);

    /** Records a reviewer's rejection feedback for a draft version; the draft is marked rejected (step 12.6). */
    void recordFeedback(Feedback feedback);
    void recordRouting(RoutingDecision decision);
    void addEvidence(List<Evidence> evidence);
    void enqueue(Task task);
    void updateTask(Task task);
    void recordExecution(ExecutionRecord execution);
    void recordBudgetUsage(BudgetUsage usage);
    void setStatus(BoardStatus status);
    void recordFailure(Failure failure);
}
