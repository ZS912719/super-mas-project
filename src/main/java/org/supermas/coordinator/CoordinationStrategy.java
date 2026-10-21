package org.supermas.coordinator;

import org.supermas.blackboard.BlackboardView;
import org.supermas.model.Domain.*;

/** Select work from current state, including supplemental retrieval, reclassification and revision. */
public interface CoordinationStrategy {
    NextAction next(BlackboardView board, ExecutionLimits limits);
}
