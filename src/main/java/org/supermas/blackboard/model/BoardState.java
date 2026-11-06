package org.supermas.blackboard.model;

import java.util.*;

import org.supermas.blackboard.api.StateConflict;
import org.supermas.model.Domain.*;

/** A transaction-local editor. No mutable state is retained between database operations. */
public final class BoardState {
    private final BlackboardSnapshot source;
    private final List<Evidence> evidence;
    private final List<Task> tasks;
    private final List<ExecutionRecord> executions;
    private final List<Draft> drafts;
    private final List<Feedback> feedback;
    private Optional<RoutingDecision> routing;
    private BoardStatus status;
    private BudgetUsage budget;
    private Optional<Failure> failure;

    public BoardState(BlackboardSnapshot source) {
        this.source = source;
        evidence = new ArrayList<>(source.evidence());
        tasks = new ArrayList<>(source.tasks());
        executions = new ArrayList<>(source.executions());
        drafts = new ArrayList<>(source.drafts());
        feedback = new ArrayList<>(source.feedback());
        routing = source.routing(); status = source.status(); budget = source.budgetUsage();
        failure = source.failure();
    }

    public void recordRouting(RoutingDecision decision) { routing = Optional.of(decision); }
    public void recordExecution(ExecutionRecord execution) { executions.add(execution); }
    public BudgetUsage budgetUsage() { return budget; }
    public void recordBudgetUsage(BudgetUsage usage) { budget = usage; }
    public void setStatus(BoardStatus value) { status = value; }
    public void recordFailure(Failure value) { failure = Optional.of(value); }

    public Task task(String id) {
        return tasks.stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> new StateConflict("Unknown task: " + id));
    }

    public void replace(Task task) { tasks.set(tasks.indexOf(task(task.id())), task); }

    public void enqueue(Task task) {
        require(source.id().equals(task.boardId()), "Task belongs to another board");
        require(task.status() == TaskStatus.PENDING, "New tasks must be pending");
        require(task.id() != null && !task.id().isBlank(), "Task identifier is required");
        require(tasks.stream().noneMatch(t -> t.id().equals(task.id())), "Duplicate task identifier");
        tasks.add(task);
    }

    public void addEvidence(List<Evidence> items, AgentConfig agent) {
        for (Evidence item : items) {
            require(source.id().scope().equals(item.scope()), "Evidence belongs to another scope");
            require(evidence.stream().noneMatch(e -> e.id().equals(item.id())), "Duplicate evidence identifier");
            if (agent != null) {
                require(agent.contextSourceIds().contains(item.provenance().sourceId()), "Source is not permitted");
                require(item.permittedAgentIds().equals(Set.of(agent.id())),
                        "Agent contributions must remain private to that agent until coordinator promotion");
            }
            evidence.add(item);
        }
    }

    /** Capabilities that allow submitting a draft (proposal P5): a drafting specialist or the composer. */
    public static final Set<String> DRAFTING_CAPABILITIES = Set.of("draft-reply", "compose-reply");

    public Draft draft(DraftProposal proposal, AgentConfig agent, java.time.Instant now) {
        String agentId = agent.id();
        require(agentId.equals(proposal.agentId()), "Draft author does not match the lease");
        require(agent.capabilities().stream().anyMatch(DRAFTING_CAPABILITIES::contains),
                "Only an agent with a drafting capability may submit a draft");
        require(drafts.stream().noneMatch(d -> d.review() == ReviewStatus.APPROVED),
                "Review invalidation is not implemented; approved drafts cannot be changed");
        Draft draft = new Draft(drafts.size() + 1, proposal.subject(), proposal.body(), agentId,
                proposal.explanation(), DraftOrigin.MODEL_GENERATED, ReviewStatus.PROPOSED, now);
        drafts.add(draft);
        return draft;
    }

    /** Feedback on an existing, unapproved version; that version is marked rejected, its content unchanged. */
    public void recordFeedback(Feedback item) {
        require(item.text() != null && !item.text().isBlank(), "Feedback text is required");
        Draft target = drafts.stream().filter(d -> d.version() == item.draftVersion()).findFirst()
                .orElseThrow(() -> new StateConflict("Unknown draft version " + item.draftVersion()));
        require(target.review() != ReviewStatus.APPROVED, "An approved draft cannot be rejected");
        drafts.set(drafts.indexOf(target), new Draft(target.version(), target.subject(), target.body(), target.agentId(),
                target.explanation(), target.origin(), ReviewStatus.REJECTED, target.createdAt()));
        feedback.add(item);
    }

    public BlackboardSnapshot snapshot() {
        return new BlackboardSnapshot(source.id(), source.email(), source.analysis(), List.copyOf(evidence),
                List.copyOf(tasks), List.copyOf(executions), List.copyOf(drafts), List.copyOf(feedback),
                routing, status, budget, failure);
    }

    public static Task transition(Task task, TaskStatus status, Optional<String> agentId) {
        // Prototype branch, step 12.1: the information need is kept (it was dropped before).
        return new Task(task.id(), task.boardId(), task.action(), agentId, status, task.reason(), task.need());
    }

    public static void require(boolean condition, String message) { if (!condition) throw new StateConflict(message); }
}
