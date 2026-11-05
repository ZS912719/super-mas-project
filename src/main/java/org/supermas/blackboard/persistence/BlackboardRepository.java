package org.supermas.blackboard.persistence;

import java.util.Optional;

import org.supermas.model.Domain.*;

/** Snapshot persistence contract. BlackboardStore adds atomic versioned commands and recovery. */
public interface BlackboardRepository {
    void save(BlackboardSnapshot snapshot);
    Optional<BlackboardSnapshot> find(BoardId boardId);
}
