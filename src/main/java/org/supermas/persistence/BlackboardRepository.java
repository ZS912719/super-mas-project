package org.supermas.persistence;

import org.supermas.model.Domain.*;
import java.util.Optional;

/** Storage format, atomicity and failure handling are deliberately left for implementation discussion. */
public interface BlackboardRepository {
    void save(BlackboardSnapshot snapshot);
    Optional<BlackboardSnapshot> find(BoardId boardId);
}
