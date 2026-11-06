package org.supermas.blackboard.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.blackboard.api.StateConflict;
import org.supermas.blackboard.model.BlackboardData.RecoveryPolicy;
import org.supermas.blackboard.model.BlackboardData.TaskLease;
import org.supermas.blackboard.testing.BlackboardTestSupport;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Feedback;
import org.supermas.model.Domain.InformationNeed;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/** The changes made to the durable blackboard on the prototype branch (step 12.1), one test each. */
class BlackboardPrototypeChangesTest extends BlackboardTestSupport {
    private static AgentConfig withCapabilities(String name, Set<String> capabilities) {
        return new AgentConfig(name, "role", "Trusted system instructions", capabilities, List.of(), Set.of("source"), false);
    }

    @Test void aClaimedAndCompletedTaskKeepsItsInformationNeed() {
        InformationNeed need = new InformationNeed(Set.of("DATE"), List.of("calendar"));
        board.enqueue(new Task("further", id, Action.RETRIEVE_CONTEXT, Optional.empty(), TaskStatus.PENDING, "more", Optional.of(need)));
        TaskLease lease = board.claim("further", agent, Duration.ofSeconds(10), RecoveryPolicy.RETRY_SAFE);
        assertEquals(Optional.of(need), task("further").need());
        board.complete(lease, TaskStatus.SUCCEEDED, "done");
        assertEquals(Optional.of(need), task("further").need());
    }

    @Test void onlyAnAgentWithADraftingCapabilityMaySubmitADraft() {
        AgentConfig context = withCapabilities("context", Set.of("topic-context"));
        TaskLease lease = board.claim("work", context, Duration.ofSeconds(10), RecoveryPolicy.RETRY_SAFE);
        assertThrows(StateConflict.class, () -> board.accessFor(lease).submitDraft(new DraftProposal("s", "b", "context", "x")));
        assertTrue(board.snapshot().drafts().isEmpty());

        board.enqueue(task("compose", id));
        AgentConfig composer = withCapabilities("composer", Set.of("compose-reply"));
        TaskLease composing = board.claim("compose", composer, Duration.ofSeconds(10), RecoveryPolicy.RETRY_SAFE);
        board.accessFor(composing).submitDraft(new DraftProposal("s", "b", "composer", "x"));
        assertEquals(1, board.snapshot().drafts().size());
    }

    @Test void aReadOnlyViewNeedsNoLeaseAndFiltersLikeTheWriteAccess() {
        board.addEvidence(List.of(evidence("mine", id.scope(), Set.of("agent")), evidence("theirs", id.scope(), Set.of("other"))));
        long revision = board.versionedSnapshot().revision();

        BlackboardView view = board.viewFor(agent);
        BlackboardSnapshot seen = view.snapshot();

        assertEquals(List.of("mine"), seen.evidence().stream().map(item -> item.id()).toList());
        assertEquals(List.of("work"), seen.tasks().stream().map(Task::id).toList(), "pending work it could take");
        assertTrue(seen.routing().isEmpty() && seen.failure().isEmpty());
        assertEquals(revision, board.versionedSnapshot().revision(), "reading writes nothing");
    }

    @Test void feedbackRejectsThatVersionAndEveryAgentWithAccessSeesIt() {
        TaskLease lease = claim(RecoveryPolicy.RETRY_SAFE);
        board.accessFor(lease).submitDraft(new DraftProposal("s", "b", agent.id(), "x"));
        board.complete(lease, TaskStatus.SUCCEEDED, "drafted");

        board.recordFeedback(new Feedback(1, "Mention the deadline", clock.instant()));

        assertEquals(ReviewStatus.REJECTED, board.snapshot().drafts().get(0).review());
        assertEquals("b", board.snapshot().drafts().get(0).body(), "content is never replaced");
        AgentConfig other = withCapabilities("other", Set.of());
        assertEquals("Mention the deadline", board.viewFor(other).snapshot().feedback().get(0).text());
        assertThrows(StateConflict.class, () -> board.recordFeedback(new Feedback(7, "x", clock.instant())));
        assertThrows(StateConflict.class, () -> board.recordFeedback(new Feedback(1, " ", clock.instant())));
    }

    private Task task(String name) {
        return board.snapshot().tasks().stream().filter(item -> item.id().equals(name)).findFirst().orElseThrow();
    }
}
