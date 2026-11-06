package org.supermas.blackboard.persistence;

import java.util.Objects;

import org.supermas.blackboard.persistence.codec.JsonCodec;
import org.supermas.model.Domain.BoardId;

/** Stable identities shared by persistence and exported document paths. */
public final class BlackboardKeys {
    private BlackboardKeys() { }

    public static String boardKey(BoardId id) {
        Objects.requireNonNull(id); Objects.requireNonNull(id.scope());
        for (String part : new String[]{id.scope().projectId(), id.scope().threadId(), id.taskId()})
            if (part == null || part.isBlank()) throw new IllegalArgumentException("Board identifiers are required");
        return JsonCodec.hash(JsonCodec.write(id));
    }
}
