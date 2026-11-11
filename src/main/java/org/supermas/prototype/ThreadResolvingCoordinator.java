package org.supermas.prototype;

import org.supermas.coordinator.Coordinator;
import org.supermas.memory.SubjectThreadFallback;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.ExecutionLimits;

/**
 * Settles the thread of each received email before the coordinator sees it: a reply without reply headers joins
 * its earlier thread under the restricted rules of {@link SubjectThreadFallback}. Everything else is delegated.
 */
// PROTOTYPE-STAND-IN: where on receipt the thread is settled belongs to the team's receive path.
// ASSUMED-TEAM-DECISION(P10): threading by subject within the same participants (owner's decision, 2026-11-11).
// Pending team validation. If rejected: drop this wrapper in PrototypeApp.Wiring; the parser's threads remain.
final class ThreadResolvingCoordinator implements Coordinator {
    private final Coordinator delegate;
    private final SubjectThreadFallback threads;

    ThreadResolvingCoordinator(Coordinator delegate, SubjectThreadFallback threads) {
        this.delegate = delegate;
        this.threads = threads;
    }

    @Override
    public BoardId receive(Email email) {
        return delegate.receive(threads.resolve(email));
    }

    @Override
    public BlackboardSnapshot run(BoardId boardId, ExecutionLimits limits) {
        return delegate.run(boardId, limits);
    }

    @Override
    public BlackboardSnapshot inspect(BoardId boardId) {
        return delegate.inspect(boardId);
    }

    @Override
    public void stop(BoardId boardId, String reason) {
        delegate.stop(boardId, reason);
    }
}
