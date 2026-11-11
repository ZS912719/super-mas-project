package org.supermas.control;

import java.util.Optional;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.NextAction;

/**
 * Stops a board before any work when the metadata agent found that the email needs no reply (plan
 * {@code docs/plans/reply-needed.md}). Used in batch runs only: when a person asks for a draft in the mailbox, the
 * agent service does not install it. Every other decision, and the control record of the wrapped strategy, is passed
 * through unchanged.
 */
public final class ReplyNeedGate implements CoordinationStrategy, ControlRecording {
    private final CoordinationStrategy delegate;

    public ReplyNeedGate(CoordinationStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public NextAction next(BlackboardView board, ExecutionLimits limits) {
        var state = board.snapshot();
        boolean fresh = state.executions().isEmpty() && state.drafts().isEmpty();
        String verdict = state.analysis() == null ? null : state.analysis().metadata().get(MetadataAgent.KEY_REPLY_NEED);
        if (fresh && "NOT_NEEDED".equals(verdict)) {
            return new NextAction(Action.STOP, Optional.empty(), "No reply needed: "
                    + state.analysis().metadata().getOrDefault(MetadataAgent.KEY_REPLY_NEED_REASONS, "[]"));
        }
        return delegate.next(board, limits);
    }

    @Override
    public Optional<ControlEntry> decision(BoardId board) {
        return delegate instanceof ControlRecording recording ? recording.decision(board) : Optional.empty();
    }
}
