package org.supermas.blackboard.model;

import java.time.Instant;
import java.util.List;

import org.supermas.model.Domain.*;

public final class BlackboardData {
    private BlackboardData() { }

    public enum RecoveryPolicy { RETRY_SAFE, REQUIRE_CONFIRMATION }

    public record VersionedSnapshot(long revision, Instant committedAt, BlackboardSnapshot snapshot,
                                    List<TaskLease> attempts) { }

    /** The token fences old attempts. The complete trusted configuration is frozen at claim time. */
    public record TaskLease(BoardId boardId, String taskId, String token, AgentConfig agent,
                            long inputRevision, Instant startedAt, Instant expiresAt,
                            RecoveryPolicy recoveryPolicy, String promptHash) { }

    public record ProjectionStatus(long desiredRevision, long acknowledgedRevision) { }
}
