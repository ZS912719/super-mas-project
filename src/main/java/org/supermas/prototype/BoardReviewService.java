package org.supermas.prototype;

import java.time.Clock;
import java.util.List;
import org.supermas.blackboard.api.StateConflict;
import org.supermas.blackboard.impl.DefaultBlackboard;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.model.Domain.Approval;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.Feedback;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.review.DraftReviewService;

// PROTOTYPE-STAND-IN: the team's human review service. Implements rejection with feedback (step 12.6), which is
// what makes revision on the blackboard possible. Edits and approval are left to the mailbox, which already
// implements version-specific approval; here they are refused rather than half implemented.
/**
 * Human review on the durable blackboard. Rejecting the current version records the feedback (that version is
 * marked rejected, its content kept) and returns the board to processing, so the next {@code Coordinator.run}
 * revises: specialists contribute again with the feedback visible, the composer writes a new version.
 */
public final class BoardReviewService implements DraftReviewService {
    private final DefaultBlackboardFactory boards;
    private final Clock clock;

    public BoardReviewService(DefaultBlackboardFactory boards, Clock clock) {
        this.boards = boards;
        this.clock = clock;
    }

    @Override
    public List<Draft> versions(BoardId boardId) {
        return boards.open(boardId).snapshot().drafts();
    }

    @Override
    public Feedback reject(BoardId boardId, int expectedVersion, String feedback) {
        DefaultBlackboard board = boards.open(boardId);
        List<Draft> drafts = board.snapshot().drafts();
        if (drafts.isEmpty() || drafts.get(drafts.size() - 1).version() != expectedVersion) {
            throw new StateConflict("Only the current draft version can be rejected");
        }
        if (drafts.get(drafts.size() - 1).review() != ReviewStatus.PROPOSED) {
            throw new StateConflict("Version " + expectedVersion + " is not awaiting review");
        }
        Feedback item = new Feedback(expectedVersion, feedback, clock.instant());
        board.recordFeedback(item);
        board.setStatus(BoardStatus.PROCESSING);
        return item;
    }

    @Override
    public Draft edit(BoardId boardId, int expectedVersion, String subject, String body) {
        throw new UnsupportedOperationException("Edits happen in the mailbox in this prototype");
    }

    @Override
    public Approval approve(BoardId boardId, int expectedVersion) {
        throw new UnsupportedOperationException("Approval happens in the mailbox in this prototype");
    }
}
