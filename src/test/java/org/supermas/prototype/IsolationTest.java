package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.supermas.prototype.Fixtures.agent;
import static org.supermas.prototype.Fixtures.analysis;
import static org.supermas.prototype.Fixtures.email;
import static org.supermas.prototype.Fixtures.evidence;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.AgentBlackboard;
import org.supermas.blackboard.Blackboard;
import org.supermas.blackboard.BlackboardView;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.Scope;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/** Equal thread identifiers in different projects, and repeat tasks in one thread, must never mix. */
class IsolationTest {
    private static final Clock CLOCK = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);
    private static final Scope PROJECT_A = new Scope("project-a", "thread-1");
    private static final Scope PROJECT_B = new Scope("project-b", "thread-1");
    private static final AgentConfig SPECIALIST =
            agent("specialist", Set.of(Capabilities.DRAFT_REPLY), Set.of("wiki"), false);

    private final InMemoryBlackboardFactory factory = new InMemoryBlackboardFactory(CLOCK);

    private static Evidence retrieved(String id, Scope scope) {
        return evidence(id, scope, EvidenceKind.RETRIEVED_EVIDENCE, "wiki", Set.of("specialist"));
    }

    @Test
    void equalThreadIdsInDifferentProjectsGetSeparateBoards() {
        Blackboard a = factory.create(email(PROJECT_A, "for a"), analysis());
        Blackboard b = factory.create(email(PROJECT_B, "for b"), analysis());
        a.addEvidence(List.of(retrieved("in-a", PROJECT_A)));
        a.accessFor(SPECIALIST).submitDraft(new DraftProposal("s", "only in a", "specialist", "e"));
        assertNotEquals(a.snapshot().id(), b.snapshot().id());
        assertEquals("for b", b.snapshot().email().body());
        assertTrue(b.snapshot().evidence().isEmpty());
        assertTrue(b.snapshot().drafts().isEmpty());
    }

    @Test
    void evidenceWithTheSameThreadFromAnotherProjectIsRejected() {
        Blackboard a = factory.create(email(PROJECT_A, "for a"), analysis());
        Evidence foreign = retrieved("foreign", PROJECT_B);
        assertThrows(SecurityException.class, () -> a.addEvidence(List.of(foreign)));
        assertThrows(SecurityException.class, () -> a.accessFor(SPECIALIST).submitEvidence(List.of(foreign)));
        assertTrue(a.snapshot().evidence().isEmpty());
    }

    @Test
    void aTaskFromTheSameThreadInAnotherProjectIsRejected() {
        Blackboard a = factory.create(email(PROJECT_A, "for a"), analysis());
        String sharedTaskId = a.snapshot().id().taskId();
        Task foreign = new Task("t", new BoardId(PROJECT_B, sharedTaskId), Action.GENERATE, Optional.empty(),
                TaskStatus.PENDING, "reason");
        assertThrows(SecurityException.class, () -> a.enqueue(foreign));
        assertThrows(SecurityException.class, () -> a.accessFor(SPECIALIST).proposeTasks(List.of(foreign)));
        assertTrue(a.snapshot().tasks().isEmpty());
    }

    @Test
    void repeatTasksInOneThreadDoNotShareABoard() {
        Blackboard first = factory.create(email(PROJECT_A, "first"), analysis());
        Blackboard second = factory.create(email(PROJECT_A, "second"), analysis());
        first.addEvidence(List.of(retrieved("e", PROJECT_A)));
        assertNotEquals(first.snapshot().id(), second.snapshot().id());
        assertTrue(second.snapshot().evidence().isEmpty());
    }

    /** End to end: during a run, the agent observes only its own project's email. */
    @Test
    void anAgentRunningThroughTheCoordinatorSeesOnlyItsOwnProject() {
        List<BlackboardSnapshot> seen = new ArrayList<>();
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        registry.register(new SpecializedAgent() {
            @Override
            public AgentConfig config() {
                return SPECIALIST;
            }

            @Override
            public AgentAssessment assess(Task task, BlackboardView board) {
                return new AgentAssessment("specialist", 1, true, "fixed");
            }

            @Override
            public AgentOutcome execute(Task task, AgentBlackboard board) {
                seen.add(board.snapshot());
                board.submitDraft(new DraftProposal("s", board.snapshot().email().body(), "specialist", "e"));
                return new AgentOutcome(TaskStatus.SUCCEEDED, "done", List.of(), Optional.empty());
            }
        });
        ScriptedCoordinator coordinator = new ScriptedCoordinator(MetadataAgent.rulesOnly(), factory, registry,
                new ScoreRoutingStrategy(0.5, 0.1), new DraftFirstCoordinationStrategy(), CLOCK);
        ExecutionLimits limits = new ExecutionLimits(5, 0, 1, 1000, 1000);

        BoardId a = coordinator.receive(email(PROJECT_A, "for a"));
        BoardId b = coordinator.receive(email(PROJECT_B, "for b"));
        BlackboardSnapshot doneA = coordinator.run(a, limits);
        BlackboardSnapshot doneB = coordinator.run(b, limits);

        assertEquals("for a", doneA.drafts().get(0).body());
        assertEquals("for b", doneB.drafts().get(0).body());
        assertEquals(1, doneB.drafts().size());
        assertEquals(PROJECT_A, seen.get(0).id().scope());
        assertEquals(PROJECT_B, seen.get(1).id().scope());
        BoardId crossed = new BoardId(PROJECT_B, a.taskId());
        assertThrows(NoSuchElementException.class, () -> coordinator.inspect(crossed));
    }
}
