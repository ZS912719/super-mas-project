package org.supermas.prototype;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.Blackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.BudgetUsage;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.DraftOrigin;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.ExecutionRecord;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.Feedback;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.model.Domain.RoutingDecision;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

// PROTOTYPE-STAND-IN: owned by the team's blackboard work, not by the Metadata/Context agents.
// Required by these agents: one isolated board per task, snapshots filtered by evidence permissions,
// and rejection of contributions from another scope or an unauthorized source.
// Replace with the real implementation.
/**
 * One task's blackboard in memory. A single lock is enough for a prototype. Permission failures throw
 * {@link SecurityException}, and malformed input throws {@link IllegalArgumentException}. Each batch is
 * checked completely before anything is stored.
 */
public final class InMemoryBlackboard implements Blackboard {
    private final Object lock = new Object();
    private final Clock clock;
    private final BoardId id;
    private final Email email;
    private final Analysis analysis;
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<Task> tasks = new ArrayList<>();
    private final List<ExecutionRecord> executions = new ArrayList<>();
    private final List<Draft> drafts = new ArrayList<>();
    private final List<Feedback> feedback = new ArrayList<>();
    private Optional<RoutingDecision> routing = Optional.empty();
    private BoardStatus status = BoardStatus.PROCESSING;
    private BudgetUsage budget = new BudgetUsage(0, 0, 0);
    private Optional<Failure> failure = Optional.empty();

    public InMemoryBlackboard(BoardId id, Email email, Analysis analysis, Clock clock) {
        this.id = Objects.requireNonNull(id);
        if (!id.scope().equals(email.scope())) {
            throw new IllegalArgumentException("The email belongs to another scope");
        }
        this.email = new Email(email.id(), email.scope(), email.sender(), List.copyOf(email.recipients()),
                email.subject(), email.body(), email.receivedAt(), Map.copyOf(email.metadata()));
        this.analysis = new Analysis(List.copyOf(analysis.keywords()), List.copyOf(analysis.initialTags()),
                Map.copyOf(analysis.metadata()));
        this.clock = clock;
    }

    /** Rebuilds a board from a full, unfiltered snapshot. */
    public static InMemoryBlackboard restore(BlackboardSnapshot snapshot, Clock clock) {
        InMemoryBlackboard board = new InMemoryBlackboard(snapshot.id(), snapshot.email(), snapshot.analysis(), clock);
        board.evidence.addAll(snapshot.evidence());
        board.tasks.addAll(snapshot.tasks());
        board.executions.addAll(snapshot.executions());
        board.drafts.addAll(snapshot.drafts());
        board.feedback.addAll(snapshot.feedback());
        board.routing = snapshot.routing();
        board.status = snapshot.status();
        board.budget = snapshot.budgetUsage();
        board.failure = snapshot.failure();
        return board;
    }

    @Override
    public BlackboardSnapshot snapshot() {
        synchronized (lock) {
            return snapshotWith(List.copyOf(evidence));
        }
    }

    private BlackboardSnapshot snapshotWith(List<Evidence> visible) {
        return new BlackboardSnapshot(id, email, analysis, visible, List.copyOf(tasks), List.copyOf(executions),
                List.copyOf(drafts), List.copyOf(feedback), routing, status, budget, failure);
    }

    @Override
    public AgentBlackboard accessFor(AgentConfig agent) {
        return new AgentAccess(agent);
    }

    /** The agent's filtered snapshot, read-only, without a task (step 12.1). */
    @Override
    public BlackboardView viewFor(AgentConfig agent) {
        Objects.requireNonNull(agent);
        return () -> {
            synchronized (lock) {
                return snapshotWith(evidence.stream()
                        .filter(item -> item.permittedAgentIds().contains(agent.id())).toList());
            }
        };
    }

    /** Feedback on an existing, unapproved version; that version is marked rejected, its content unchanged. */
    @Override
    public void recordFeedback(Feedback item) {
        synchronized (lock) {
            if (item.text() == null || item.text().isBlank()) {
                throw new IllegalArgumentException("Feedback text is required");
            }
            Draft target = drafts.stream().filter(draft -> draft.version() == item.draftVersion()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown draft version " + item.draftVersion()));
            if (target.review() == ReviewStatus.APPROVED) {
                throw new IllegalStateException("An approved draft cannot be rejected");
            }
            drafts.set(drafts.indexOf(target), new Draft(target.version(), target.subject(), target.body(),
                    target.agentId(), target.explanation(), target.origin(), ReviewStatus.REJECTED, target.createdAt()));
            feedback.add(item);
        }
    }

    @Override
    public void recordRouting(RoutingDecision decision) {
        synchronized (lock) {
            routing = Optional.of(decision);
        }
    }

    @Override
    public void addEvidence(List<Evidence> added) {
        synchronized (lock) {
            added.forEach(this::requireStorable);
            requireDistinctIds(added);
            added.stream().map(InMemoryBlackboard::copy).forEach(evidence::add);
        }
    }

    @Override
    public void enqueue(Task task) {
        synchronized (lock) {
            requireNewTask(task);
            tasks.add(task);
        }
    }

    @Override
    public void updateTask(Task task) {
        synchronized (lock) {
            for (int i = 0; i < tasks.size(); i++) {
                if (tasks.get(i).id().equals(task.id())) {
                    requireThisBoard(task);
                    tasks.set(i, task);
                    return;
                }
            }
            throw new IllegalArgumentException("Unknown task " + task.id());
        }
    }

    @Override
    public void recordExecution(ExecutionRecord execution) {
        synchronized (lock) {
            executions.add(execution);
        }
    }

    @Override
    public void recordBudgetUsage(BudgetUsage usage) {
        synchronized (lock) {
            budget = usage;
        }
    }

    @Override
    public void setStatus(BoardStatus newStatus) {
        synchronized (lock) {
            status = newStatus;
        }
    }

    @Override
    public void recordFailure(Failure newFailure) {
        synchronized (lock) {
            failure = Optional.of(newFailure);
        }
    }

    private void requireStorable(Evidence item) {
        if (!id.scope().equals(item.scope())) {
            throw new SecurityException("Evidence " + item.id() + " belongs to another scope");
        }
        if (evidence.stream().anyMatch(existing -> existing.id().equals(item.id()))) {
            throw new IllegalArgumentException("Evidence " + item.id() + " already exists");
        }
    }

    private static void requireDistinctIds(List<Evidence> items) {
        if (items.stream().map(Evidence::id).distinct().count() != items.size()) {
            throw new IllegalArgumentException("Evidence identifiers repeat within one submission");
        }
    }

    private void requireThisBoard(Task task) {
        if (!id.equals(task.boardId())) {
            throw new SecurityException("Task " + task.id() + " belongs to another board");
        }
    }

    private void requireNewTask(Task task) {
        requireThisBoard(task);
        if (tasks.stream().anyMatch(existing -> existing.id().equals(task.id()))) {
            throw new IllegalArgumentException("Task " + task.id() + " already exists");
        }
    }

    private static Evidence copy(Evidence item) {
        return new Evidence(item.id(), item.scope(), item.kind(), item.provenance(), item.text(),
                Set.copyOf(item.permittedAgentIds()));
    }

    /** What one agent may see and do. The agent's identity comes from here, never from its input. */
    private final class AgentAccess implements AgentBlackboard {
        private final AgentConfig agent;

        AgentAccess(AgentConfig agent) {
            this.agent = Objects.requireNonNull(agent);
        }

        @Override
        public String agentId() {
            return agent.id();
        }

        @Override
        public BlackboardSnapshot snapshot() {
            synchronized (lock) {
                return snapshotWith(evidence.stream()
                        .filter(item -> item.permittedAgentIds().contains(agent.id())).toList());
            }
        }

        @Override
        public void submitEvidence(List<Evidence> submitted) {
            synchronized (lock) {
                for (Evidence item : submitted) {
                    requireStorable(item);
                    requireAuthorized(item);
                }
                requireDistinctIds(submitted);
                submitted.stream().map(InMemoryBlackboard::copy).forEach(evidence::add);
            }
        }

        /** Keeps provenance honest: an agent cannot claim another source, or present an inference as a fact. */
        private void requireAuthorized(Evidence item) {
            String source = item.provenance().sourceId();
            boolean allowed = item.kind() == EvidenceKind.RETRIEVED_EVIDENCE && agent.contextSourceIds().contains(source)
                    || item.kind() == EvidenceKind.MODEL_INFERENCE && agent.id().equals(source);
            if (!allowed) {
                throw new SecurityException("Agent " + agent.id() + " may not submit " + item.kind()
                        + " from source " + source);
            }
            if (!item.permittedAgentIds().contains(agent.id())) {
                throw new IllegalArgumentException("Evidence " + item.id() + " would be hidden from its own author");
            }
        }

        // ASSUMED-TEAM-DECISION(P5): only agents with the drafting capability may submit drafts. Pending team validation.
        // If rejected: remove this check, or apply the team's permission rule.
        @Override
        public Draft submitDraft(DraftProposal proposal) {
            if (!agent.capabilities().contains(Capabilities.DRAFT_REPLY)) {
                throw new SecurityException("Agent " + agent.id() + " may not draft replies");
            }
            if (!agent.id().equals(proposal.agentId())) {
                throw new SecurityException("A draft must be submitted by the agent it names");
            }
            synchronized (lock) {
                // Content stays untouched: only the older proposal's review state changes.
                for (int i = 0; i < drafts.size(); i++) {
                    Draft old = drafts.get(i);
                    if (old.review() == ReviewStatus.PROPOSED) {
                        drafts.set(i, new Draft(old.version(), old.subject(), old.body(), old.agentId(),
                                old.explanation(), old.origin(), ReviewStatus.SUPERSEDED, old.createdAt()));
                    }
                }
                int version = drafts.stream().mapToInt(Draft::version).max().orElse(0) + 1;
                Draft draft = new Draft(version, proposal.subject(), proposal.body(), agent.id(),
                        proposal.explanation(), DraftOrigin.MODEL_GENERATED, ReviewStatus.PROPOSED, clock.instant());
                drafts.add(draft);
                return draft;
            }
        }

        /** Always pending, and never aimed at a peer: agents suggest work but do not choose other agents. */
        @Override
        public void proposeTasks(List<Task> proposed) {
            synchronized (lock) {
                for (Task task : proposed) {
                    requireNewTask(task);
                    if (task.agentId().isPresent() && !task.agentId().get().equals(agent.id())) {
                        throw new SecurityException("Agent " + agent.id() + " may not assign work to a peer");
                    }
                }
                proposed.forEach(task -> tasks.add(new Task(task.id(), task.boardId(), task.action(), task.agentId(),
                        TaskStatus.PENDING, task.reason(), task.need())));
            }
        }
    }
}
