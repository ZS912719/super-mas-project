package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.DraftOrigin;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.model.Domain.Scope;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

class InMemoryBlackboardTest {
    private static final Scope SCOPE = new Scope("project", "thread");
    private static final BoardId ID = new BoardId(SCOPE, "task");
    private static final Clock CLOCK = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);
    private static final AgentConfig WRITER = agent("writer", Set.of(Capabilities.DRAFT_REPLY), Set.of("wiki"), false);
    private static final AgentConfig READER = agent("reader", Set.of(), Set.of("mail"), false);

    private final InMemoryBlackboard board = new InMemoryBlackboard(ID, email(SCOPE, "body"), analysis(), CLOCK);

    private static DraftProposal proposal(String agentId, String body) {
        return new DraftProposal("subject", body, agentId, "why");
    }

    private static Task task(String id, BoardId boardId, Optional<String> agentId) {
        return new Task(id, boardId, Action.GENERATE, agentId, TaskStatus.RUNNING, "reason");
    }

    @Test
    void agentsSeeOnlyEvidencePermittedToThem() {
        board.addEvidence(List.of(
                evidence("shared", SCOPE, EvidenceKind.ORIGINAL_FACT, "mail", Set.of("writer", "reader")),
                evidence("private", SCOPE, EvidenceKind.RETRIEVED_EVIDENCE, "wiki", Set.of("writer"))));
        assertEquals(List.of("shared"), board.accessFor(READER).snapshot().evidence().stream().map(Evidence::id).toList());
        assertEquals(2, board.accessFor(WRITER).snapshot().evidence().size());
        assertEquals(2, board.snapshot().evidence().size());
    }

    @Test
    void anAgentCannotSubmitFromAnUnauthorizedSource() {
        Evidence stolen = evidence("e", SCOPE, EvidenceKind.RETRIEVED_EVIDENCE, "wiki", Set.of("reader"));
        assertThrows(SecurityException.class, () -> board.accessFor(READER).submitEvidence(List.of(stolen)));
        assertTrue(board.snapshot().evidence().isEmpty());
    }

    @Test
    void anAgentCannotPresentItsOutputAsAnOriginalFactOrAnotherAgentsInference() {
        AgentBlackboard access = board.accessFor(WRITER);
        assertThrows(SecurityException.class, () -> access.submitEvidence(List.of(
                evidence("fact", SCOPE, EvidenceKind.ORIGINAL_FACT, "wiki", Set.of("writer")))));
        assertThrows(SecurityException.class, () -> access.submitEvidence(List.of(
                evidence("guess", SCOPE, EvidenceKind.MODEL_INFERENCE, "reader", Set.of("writer")))));
        access.submitEvidence(List.of(evidence("own", SCOPE, EvidenceKind.MODEL_INFERENCE, "writer", Set.of("writer"))));
        assertEquals(1, board.snapshot().evidence().size());
    }

    @Test
    void aRejectedItemRejectsTheWholeBatch() {
        List<Evidence> batch = List.of(
                evidence("good", SCOPE, EvidenceKind.RETRIEVED_EVIDENCE, "wiki", Set.of("writer")),
                evidence("bad", SCOPE, EvidenceKind.RETRIEVED_EVIDENCE, "secret", Set.of("writer")));
        assertThrows(SecurityException.class, () -> board.accessFor(WRITER).submitEvidence(batch));
        assertTrue(board.snapshot().evidence().isEmpty());
    }

    @Test
    void repeatedEvidenceIdentifiersAreRejected() {
        Evidence item = evidence("same", SCOPE, EvidenceKind.ORIGINAL_FACT, "mail", Set.of());
        assertThrows(IllegalArgumentException.class, () -> board.addEvidence(List.of(item, item)));
        board.addEvidence(List.of(item));
        assertThrows(IllegalArgumentException.class, () -> board.addEvidence(List.of(item)));
    }

    @Test
    void onlyDraftingAgentsMayDraftAndOnlyInTheirOwnName() {
        assertThrows(SecurityException.class, () -> board.accessFor(READER).submitDraft(proposal("reader", "x")));
        assertThrows(SecurityException.class, () -> board.accessFor(WRITER).submitDraft(proposal("reader", "x")));
        assertTrue(board.snapshot().drafts().isEmpty());
    }

    @Test
    void draftsAreVersionedAndOldContentIsKept() {
        AgentBlackboard access = board.accessFor(WRITER);
        access.submitDraft(proposal("writer", "first"));
        Draft second = access.submitDraft(proposal("writer", "second"));
        List<Draft> drafts = board.snapshot().drafts();
        assertEquals(2, second.version());
        assertEquals(DraftOrigin.MODEL_GENERATED, second.origin());
        assertEquals(ReviewStatus.PROPOSED, second.review());
        assertEquals("first", drafts.get(0).body());
        assertEquals(ReviewStatus.SUPERSEDED, drafts.get(0).review());
    }

    @Test
    void proposedTasksArePendingAndCannotTargetAPeerOrAnotherBoard() {
        AgentBlackboard access = board.accessFor(WRITER);
        access.proposeTasks(List.of(task("t1", ID, Optional.empty())));
        assertEquals(TaskStatus.PENDING, board.snapshot().tasks().get(0).status());
        assertThrows(SecurityException.class, () -> access.proposeTasks(List.of(task("t2", ID, Optional.of("reader")))));
        BoardId other = new BoardId(SCOPE, "other-task");
        assertThrows(SecurityException.class, () -> access.proposeTasks(List.of(task("t3", other, Optional.empty()))));
        assertEquals(1, board.snapshot().tasks().size());
    }

    @Test
    void laterChangesToTheCallersListsDoNotReachTheBoard() {
        List<String> recipients = new ArrayList<>(List.of("d@e.f"));
        Email email = new Email("1", SCOPE, "a@b.c", recipients, "s", "b", Fixtures.NOW, Map.of());
        InMemoryBlackboard own = new InMemoryBlackboard(ID, email, analysis(), CLOCK);
        recipients.add("attacker@x.y");
        assertEquals(1, own.snapshot().email().recipients().size());
        assertThrows(UnsupportedOperationException.class, () -> own.snapshot().evidence().add(null));
    }

    @Test
    void aSnapshotRestoresACompleteBoard() {
        board.addEvidence(List.of(evidence("e", SCOPE, EvidenceKind.ORIGINAL_FACT, "mail", Set.of())));
        board.accessFor(WRITER).submitDraft(proposal("writer", "text"));
        assertEquals(board.snapshot(), new InMemoryBlackboardFactory(CLOCK).restore(board.snapshot()).snapshot());
    }

    @Test
    void anEmailFromAnotherScopeCannotFoundABoard() {
        Email foreign = email(new Scope("other", "thread"), "body");
        assertThrows(IllegalArgumentException.class, () -> new InMemoryBlackboard(ID, foreign, analysis(), CLOCK));
    }

    @Test
    void aProposedTaskKeepsItsInformationNeed() {
        org.supermas.model.Domain.Scope scope = new org.supermas.model.Domain.Scope("p", "t");
        org.supermas.model.Domain.BoardId id = new org.supermas.model.Domain.BoardId(scope, "task");
        InMemoryBlackboard board = new InMemoryBlackboard(id, Fixtures.email(scope, "body"), Fixtures.analysis(),
                java.time.Clock.systemUTC());
        var need = new org.supermas.model.Domain.InformationNeed(java.util.Set.of("DATE"), java.util.List.of("schedule"));
        board.accessFor(Fixtures.agent("a", java.util.Set.of(), java.util.Set.of(), false)).proposeTasks(java.util.List.of(
                new org.supermas.model.Domain.Task("need", id, org.supermas.model.Domain.Action.RETRIEVE_CONTEXT,
                        java.util.Optional.empty(), org.supermas.model.Domain.TaskStatus.RUNNING, "r",
                        java.util.Optional.of(need))));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Optional.of(need), board.snapshot().tasks().get(0).need());
    }
}
