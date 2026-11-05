package org.supermas.blackboard.persistence;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.supermas.blackboard.model.BlackboardData.*;
import org.supermas.blackboard.model.BoardState;
import org.supermas.model.Domain.*;

/** Coordinator-side storage contract. Implementations must serialize commands atomically. */
public interface BlackboardStore extends BlackboardRepository {
    Clock clock();
    String databaseId();
    Optional<VersionedSnapshot> latest(BoardId id);
    Optional<VersionedSnapshot> version(BoardId id, long revision);

    /** Apply the editor only within a short write transaction; never call external services in it. */
    VersionedSnapshot edit(BoardId id, Consumer<BoardState> action);
    TaskLease claim(BoardId id, String taskId, AgentConfig config, Duration duration, RecoveryPolicy policy);
    TaskLease resolveLease(TaskLease supplied);
    TaskLease heartbeat(TaskLease supplied, Duration duration);
    void contribute(TaskLease lease, BiConsumer<BoardState, AgentConfig> action);
    VersionedSnapshot complete(TaskLease supplied, TaskStatus status, String summary);
    int recoverExpired(BoardId id);
    void abandon(TaskLease supplied);

    ProjectionStatus projectionStatus(BoardId id);
    List<BoardId> boards();
    void acknowledge(BoardId id, long revision);
}
