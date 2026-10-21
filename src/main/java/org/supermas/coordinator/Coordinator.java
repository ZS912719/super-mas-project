package org.supermas.coordinator;

import org.supermas.model.Domain.*;

/** Orchestration contract only; no scheduling loop or retry policy has been implemented. */
public interface Coordinator {
    /** Preprocess input, create a task blackboard and associate authorized thread context. */
    BoardId receive(Email email);

    /** Advance until review, failure or stop; enforce cumulative round, retry and generation budgets. */
    BlackboardSnapshot run(BoardId boardId, ExecutionLimits limits);

    BlackboardSnapshot inspect(BoardId boardId);
    void stop(BoardId boardId, String reason);
}
