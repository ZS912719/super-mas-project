package org.supermas.blackboard.api;

import org.supermas.model.Domain.BlackboardSnapshot;

/** Read-only view. Implementations must return defensive snapshots limited to the authorized scope. */
public interface BlackboardView {
    BlackboardSnapshot snapshot();
}
