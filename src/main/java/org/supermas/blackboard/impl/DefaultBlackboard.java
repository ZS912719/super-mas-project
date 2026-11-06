package org.supermas.blackboard.impl;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.supermas.blackboard.api.*;
import org.supermas.blackboard.api.StateConflict;
import org.supermas.blackboard.model.BlackboardData.*;
import org.supermas.blackboard.persistence.BlackboardStore;
import org.supermas.model.Domain.*;

import static org.supermas.blackboard.model.BoardState.require;

/** Coordinator handle. Do not give this handle or the store to specialist agents. */
public final class DefaultBlackboard implements Blackboard {
    private final BlackboardStore store;
    private final BoardId id;

    public DefaultBlackboard(BlackboardStore store, BoardId id) {
        this.store = Objects.requireNonNull(store); this.id = Objects.requireNonNull(id);
        require(store.latest(id).isPresent(), "Unknown board");
    }

    public VersionedSnapshot versionedSnapshot() { return store.latest(id).orElseThrow(); }
    @Override public BlackboardSnapshot snapshot() { return versionedSnapshot().snapshot(); }

    public TaskLease claim(String taskId, AgentConfig agent, Duration duration, RecoveryPolicy policy) {
        return store.claim(id, taskId, agent, duration, policy);
    }

    public TaskLease heartbeat(TaskLease lease, Duration duration) {
        require(id.equals(lease.boardId()), "Lease belongs to another board");
        return store.heartbeat(lease, duration);
    }

    public VersionedSnapshot complete(TaskLease lease, TaskStatus status, String summary) {
        require(id.equals(lease.boardId()), "Lease belongs to another board");
        return store.complete(lease, status, summary);
    }

    public int recoverExpired() { return store.recoverExpired(id); }
    public void abandon(TaskLease lease) {
        require(id.equals(lease.boardId()), "Lease belongs to another board");
        store.abandon(lease);
    }

    /** A lease is mandatory for specialist mutations; use accessFor(TaskLease) after claiming. */
    @Override public AgentBlackboard accessFor(AgentConfig agent) {
        throw new StateConflict("Claim a task and use accessFor(TaskLease)");
    }

    public AgentBlackboard accessFor(TaskLease supplied) {
        require(id.equals(supplied.boardId()), "Lease belongs to another board");
        // Resolve configuration from SQLite rather than trusting fields in a caller-built token object.
        TaskLease lease = store.resolveLease(supplied);
        return new AgentBlackboard() {
            @Override public String agentId() { return lease.agent().id(); }

            @Override public BlackboardSnapshot snapshot() {
                store.resolveLease(lease);
                return restricted(DefaultBlackboard.this.snapshot(), lease.agent(), Optional.of(lease.taskId()));
            }

            @Override public void submitEvidence(List<Evidence> evidence) {
                store.contribute(lease, (state, agent) -> state.addEvidence(evidence, agent));
            }

            @Override public Draft submitDraft(DraftProposal proposal) {
                AtomicReference<Draft> result = new AtomicReference<>();
                store.contribute(lease, (state, agent) -> result.set(state.draft(proposal, agent, store.clock().instant())));
                return result.get();
            }

            @Override public void proposeTasks(List<Task> tasks) {
                store.contribute(lease, (state, agent) -> {
                    for (Task task : tasks) {
                        require(task.agentId().isEmpty(), "Specialists cannot assign work to peers");
                        state.enqueue(task);
                    }
                });
            }
        };
    }

    /** Read-only and lease-free: the same filtering as {@link #accessFor(TaskLease)}, without the task's records. */
    @Override public BlackboardView viewFor(AgentConfig agent) {
        Objects.requireNonNull(agent);
        return () -> restricted(DefaultBlackboard.this.snapshot(), agent, Optional.empty());
    }

    /**
     * What one agent may see. Evidence only from its sources and readable by it; its own drafts; with a lease,
     * its task and that task's records, without one the pending work it could take. Feedback is shown in full:
     * it concerns the reply, which several specialists build (prototype branch, step 12.1). Routing and global
     * failures stay hidden.
     */
    private static BlackboardSnapshot restricted(BlackboardSnapshot all, AgentConfig agent, Optional<String> taskId) {
        List<Evidence> evidence = all.evidence().stream().filter(e ->
                agent.contextSourceIds().contains(e.provenance().sourceId()) &&
                e.permittedAgentIds().contains(agent.id())).toList();
        List<Task> tasks = all.tasks().stream().filter(t -> taskId.isPresent() ? t.id().equals(taskId.get())
                : t.status() == TaskStatus.PENDING && (t.agentId().isEmpty() || t.agentId().get().equals(agent.id())))
                .toList();
        List<Draft> drafts = all.drafts().stream().filter(d -> d.agentId().equals(agent.id())).toList();
        return new BlackboardSnapshot(all.id(), all.email(), all.analysis(), evidence, tasks,
                all.executions().stream().filter(e -> taskId.isPresent() && e.taskId().equals(taskId.get())).toList(),
                drafts, all.feedback(), Optional.empty(), all.status(), all.budgetUsage(), Optional.empty());
    }

    @Override public void recordFeedback(Feedback feedback) { store.edit(id, s -> s.recordFeedback(feedback)); }

    @Override public void recordRouting(RoutingDecision decision) { store.edit(id, s -> s.recordRouting(decision)); }
    @Override public void addEvidence(List<Evidence> evidence) { store.edit(id, s -> s.addEvidence(evidence, null)); }
    @Override public void enqueue(Task task) { store.edit(id, s -> s.enqueue(task)); }

    /** Running work can only change through a fenced execution command. */
    @Override public void updateTask(Task task) {
        store.edit(id, s -> {
            Task old = s.task(task.id());
            require(task.boardId().equals(id), "Task belongs to another board");
            require(old.status() == TaskStatus.PENDING && task.status() == TaskStatus.CANCELLED,
                    "Only pending-task cancellation is supported; use claim/complete/recovery for execution");
            s.replace(task);
        });
    }

    @Override public void recordExecution(ExecutionRecord execution) {
        throw new StateConflict("Execution records are generated by complete/recovery");
    }

    @Override public void recordBudgetUsage(BudgetUsage usage) {
        store.edit(id, s -> {
            require(usage.rounds() >= s.budgetUsage().rounds() && usage.retries() >= s.budgetUsage().retries()
                    && usage.generationAttempts() >= s.budgetUsage().generationAttempts(), "Budget counters cannot decrease");
            s.recordBudgetUsage(usage);
        });
    }

    @Override public void setStatus(BoardStatus status) {
        require(status == BoardStatus.PROCESSING || status == BoardStatus.AWAITING_REVIEW
                || status == BoardStatus.FAILED || status == BoardStatus.STOPPED,
                "Approval requires the separate human review service");
        store.edit(id, s -> s.setStatus(status));
    }
    @Override public void recordFailure(Failure failure) { store.edit(id, s -> s.recordFailure(failure)); }
}
