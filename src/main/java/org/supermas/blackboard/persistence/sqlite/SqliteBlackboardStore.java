package org.supermas.blackboard.persistence.sqlite;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

import org.supermas.blackboard.api.StateConflict;
import org.supermas.blackboard.model.BlackboardData.*;
import org.supermas.blackboard.model.BoardState;
import org.supermas.blackboard.persistence.BlackboardStore;
import org.supermas.blackboard.persistence.StorageException;
import org.supermas.blackboard.persistence.codec.JsonCodec;
import org.supermas.model.Domain.*;

import static org.supermas.blackboard.model.BoardState.require;
import static org.supermas.blackboard.persistence.BlackboardKeys.boardKey;

/** Local SQLite authority. Every command uses a separate connection and a short IMMEDIATE transaction. */
public final class SqliteBlackboardStore implements BlackboardStore {
    private final String url;
    private final Clock clock;

    public SqliteBlackboardStore(Path database) { this(database, Clock.systemUTC()); }

    public SqliteBlackboardStore(Path database, Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        Path path = database.toAbsolutePath().normalize();
        try { Files.createDirectories(path.getParent()); }
        catch (java.io.IOException e) { throw new StorageException("Cannot create database directory", e); }
        url = "jdbc:sqlite:" + path;
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
        } catch (SQLException e) { throw new StorageException("Cannot enable WAL", e); }
        transaction(c -> {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA user_version")) {
                r.next();
                require(r.getInt(1) <= 1, "Database schema is newer than this application");
            }
            execute(c, "CREATE TABLE IF NOT EXISTS boards (board_key TEXT PRIMARY KEY, revision INTEGER NOT NULL)");
            execute(c, "CREATE TABLE IF NOT EXISTS metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)");
            execute(c, "INSERT OR IGNORE INTO metadata VALUES ('database_id', ?)", UUID.randomUUID().toString());
            execute(c, "CREATE TABLE IF NOT EXISTS snapshots (board_key TEXT NOT NULL REFERENCES boards(board_key), revision INTEGER NOT NULL, committed_at TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(board_key, revision))");
            execute(c, "CREATE TABLE IF NOT EXISTS leases (board_key TEXT NOT NULL, task_id TEXT NOT NULL, token TEXT NOT NULL UNIQUE, expected_revision INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(board_key, task_id), FOREIGN KEY(board_key) REFERENCES boards(board_key))");
            execute(c, "CREATE TABLE IF NOT EXISTS attempts (token TEXT PRIMARY KEY, board_key TEXT NOT NULL REFERENCES boards(board_key), input_revision INTEGER NOT NULL, payload TEXT NOT NULL)");
            execute(c, "CREATE INDEX IF NOT EXISTS attempts_by_board_revision ON attempts(board_key, input_revision)");
            execute(c, "CREATE TABLE IF NOT EXISTS receipts (token TEXT PRIMARY KEY, request TEXT NOT NULL, revision INTEGER NOT NULL, board_key TEXT NOT NULL REFERENCES boards(board_key))");
            execute(c, "CREATE TABLE IF NOT EXISTS projections (board_key TEXT PRIMARY KEY REFERENCES boards(board_key), desired INTEGER NOT NULL, acknowledged INTEGER NOT NULL DEFAULT 0)");
            execute(c, "PRAGMA user_version=1");
            return null;
        });
    }

    public Clock clock() { return clock; }
    public String databaseId() {
        try (Connection c = connection(); PreparedStatement p = prepare(c, "SELECT value FROM metadata WHERE name='database_id'");
             ResultSet r = p.executeQuery()) { r.next(); return r.getString(1); }
        catch (SQLException e) { throw new StorageException("Cannot read database identity", e); }
    }
    private Connection connection() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA synchronous=FULL");
        } catch (SQLException e) { c.close(); throw e; }
        return c;
    }

    @FunctionalInterface interface Transaction<T> { T run(Connection c) throws SQLException; }

    <T> T transaction(Transaction<T> action) {
        try (Connection c = connection()) {
            execute(c, "BEGIN IMMEDIATE");
            try {
                T result = action.run(c);
                execute(c, "COMMIT");
                return result;
            } catch (SQLException | RuntimeException e) {
                try { execute(c, "ROLLBACK"); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw new StorageException("SQLite command failed; retry only idempotent commands", e); }
    }

    static void execute(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement p = prepare(c, sql, values)) { p.executeUpdate(); }
    }

    static PreparedStatement prepare(Connection c, String sql, Object... values) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql);
        for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
        return p;
    }

    /** Legacy save is deliberately insert-only: blind overwrites are not safe. */
    @Override public void save(BlackboardSnapshot snapshot) {
        require(snapshot.id().scope().equals(snapshot.email().scope()), "Email scope does not match board");
        require(snapshot.tasks().stream().allMatch(t -> t.boardId().equals(snapshot.id()) && t.status() != TaskStatus.RUNNING),
                "Initial snapshot cannot contain foreign or running tasks");
        require(snapshot.evidence().stream().allMatch(e -> e.scope().equals(snapshot.id().scope())), "Foreign evidence");
        require(snapshot.tasks().stream().map(Task::id).distinct().count() == snapshot.tasks().size(), "Duplicate tasks");
        transaction(c -> {
            String key = boardKey(snapshot.id());
            require(read(c, key, null).isEmpty(), "Board already exists; use versioned commands");
            execute(c, "INSERT INTO boards VALUES (?, 0)", key);
            commit(c, key, 1, snapshot);
            return null;
        });
    }

    @Override public Optional<BlackboardSnapshot> find(BoardId id) { return latest(id).map(VersionedSnapshot::snapshot); }

    public Optional<VersionedSnapshot> latest(BoardId id) { return read(boardKey(id), null); }
    public Optional<VersionedSnapshot> version(BoardId id, long revision) { return read(boardKey(id), revision); }

    private Optional<VersionedSnapshot> read(String key, Long revision) {
        try (Connection c = connection()) { return read(c, key, revision); }
        catch (SQLException e) { throw new StorageException("Cannot read snapshot", e); }
    }

    private Optional<VersionedSnapshot> read(Connection c, String key, Long revision) throws SQLException {
        String query = revision == null
                ? "SELECT s.* FROM snapshots s JOIN boards b ON s.board_key=b.board_key AND s.revision=b.revision WHERE s.board_key=?"
                : "SELECT * FROM snapshots WHERE board_key=? AND revision=?";
        try (PreparedStatement p = revision == null ? prepare(c, query, key) : prepare(c, query, key, revision);
             ResultSet r = p.executeQuery()) {
            if (!r.next()) return Optional.empty();
            return Optional.of(new VersionedSnapshot(r.getLong("revision"), Instant.parse(r.getString("committed_at")),
                    JsonCodec.read(r.getString("payload"), BlackboardSnapshot.class), attemptsAt(c, key, r.getLong("revision"))));
        }
    }

    private List<TaskLease> attemptsAt(Connection c, String key, long revision) throws SQLException {
        try (PreparedStatement p = prepare(c, "SELECT payload FROM attempts WHERE board_key=? AND input_revision<=? ORDER BY input_revision", key, revision);
             ResultSet r = p.executeQuery()) {
            List<TaskLease> attempts = new ArrayList<>();
            while (r.next()) attempts.add(JsonCodec.read(r.getString(1), TaskLease.class));
            return List.copyOf(attempts);
        }
    }

    private VersionedSnapshot current(Connection c, String key) throws SQLException {
        return read(c, key, null).orElseThrow(() -> new StateConflict("Unknown board"));
    }

    private VersionedSnapshot commit(Connection c, String key, long revision, BlackboardSnapshot snapshot) throws SQLException {
        Instant now = clock.instant();
        execute(c, "INSERT INTO snapshots VALUES (?, ?, ?, ?)", key, revision, now.toString(), JsonCodec.write(snapshot));
        execute(c, "UPDATE boards SET revision=? WHERE board_key=?", revision, key);
        execute(c, "INSERT INTO projections(board_key, desired) VALUES (?, ?) ON CONFLICT(board_key) DO UPDATE SET desired=excluded.desired", key, revision);
        return new VersionedSnapshot(revision, now, snapshot, attemptsAt(c, key, revision));
    }

    public VersionedSnapshot edit(BoardId id, Consumer<BoardState> action) {
        return transaction(c -> {
            String key = boardKey(id); VersionedSnapshot before = current(c, key);
            BoardState state = new BoardState(before.snapshot()); action.accept(state);
            return commit(c, key, before.revision() + 1, state.snapshot());
        });
    }

    public TaskLease claim(BoardId id, String taskId, AgentConfig config, Duration duration, RecoveryPolicy policy) {
        validDuration(duration); Objects.requireNonNull(policy);
        require(config.id() != null && !config.id().isBlank(), "Agent identifier is required");
        AgentConfig agent = JsonCodec.read(JsonCodec.write(config), AgentConfig.class);
        return transaction(c -> {
            String key = boardKey(id); VersionedSnapshot before = current(c, key);
            require(before.snapshot().status() == BoardStatus.PROCESSING, "Board is not processing");
            BoardState state = new BoardState(before.snapshot()); Task task = state.task(taskId);
            require(task.status() == TaskStatus.PENDING, "Task is not pending");
            require(task.agentId().isEmpty() || task.agentId().get().equals(agent.id()), "Task is assigned to another agent");
            Instant now = clock.instant(); long revision = before.revision() + 1;
            TaskLease lease = new TaskLease(id, taskId, UUID.randomUUID().toString(), agent, revision,
                    now, now.plus(duration), policy, JsonCodec.hash(agent.systemPrompt()));
            state.replace(BoardState.transition(task, TaskStatus.RUNNING, Optional.of(agent.id())));
            execute(c, "INSERT INTO leases VALUES (?, ?, ?, ?, ?)", key, taskId, lease.token(), revision, JsonCodec.write(lease));
            execute(c, "INSERT INTO attempts VALUES (?, ?, ?, ?)", lease.token(), key, revision, JsonCodec.write(lease));
            commit(c, key, revision, state.snapshot()); return lease;
        });
    }

    private static void validDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Lease duration must be positive and at most one hour");
    }

    private record Active(TaskLease lease, long expectedRevision) { }
    public TaskLease resolveLease(TaskLease supplied) {
        return transaction(c -> active(c, supplied).lease());
    }
    private Active active(Connection c, TaskLease supplied) throws SQLException {
        try (PreparedStatement p = prepare(c, "SELECT * FROM leases WHERE board_key=? AND task_id=? AND token=?",
                boardKey(supplied.boardId()), supplied.taskId(), supplied.token()); ResultSet r = p.executeQuery()) {
            require(r.next(), "Execution attempt is no longer active");
            TaskLease actual = JsonCodec.read(r.getString("payload"), TaskLease.class);
            require(actual.expiresAt().isAfter(clock.instant()), "Lease has expired");
            return new Active(actual, r.getLong("expected_revision"));
        }
    }

    public TaskLease heartbeat(TaskLease supplied, Duration duration) {
        validDuration(duration);
        return transaction(c -> {
            Active active = active(c, supplied);
            require(current(c, boardKey(supplied.boardId())).revision() == active.expectedRevision(),
                    "Input state changed; abandon this attempt before retrying");
            TaskLease old = active.lease();
            TaskLease renewed = new TaskLease(old.boardId(), old.taskId(), old.token(), old.agent(), old.inputRevision(),
                    old.startedAt(), clock.instant().plus(duration), old.recoveryPolicy(), old.promptHash());
            execute(c, "UPDATE leases SET payload=? WHERE token=?", JsonCodec.write(renewed), old.token());
            return renewed;
        });
    }

    public void contribute(TaskLease lease, java.util.function.BiConsumer<BoardState, AgentConfig> action) {
        transaction(c -> {
            String key = boardKey(lease.boardId()); Active active = active(c, lease);
            VersionedSnapshot before = current(c, key);
            require(before.revision() == active.expectedRevision(), "Input state changed; restart the task");
            require(before.snapshot().status() == BoardStatus.PROCESSING, "Board is not processing");
            BoardState state = new BoardState(before.snapshot()); action.accept(state, active.lease().agent());
            commit(c, key, before.revision() + 1, state.snapshot());
            execute(c, "UPDATE leases SET expected_revision=? WHERE token=?", before.revision() + 1, lease.token());
            return null;
        });
    }

    /** One terminal request per attempt. Exact duplicate requests return the original committed version. */
    public VersionedSnapshot complete(TaskLease supplied, TaskStatus status, String summary) {
        require(status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED, "Expected a terminal execution status");
        Objects.requireNonNull(summary);
        String request = JsonCodec.write(List.of(status, summary));
        return transaction(c -> {
            String key = boardKey(supplied.boardId());
            try (PreparedStatement p = prepare(c, "SELECT * FROM receipts WHERE token=?", supplied.token()); ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    require(key.equals(r.getString("board_key")) && request.equals(r.getString("request")), "Conflicting duplicate completion");
                    return read(c, key, r.getLong("revision")).orElseThrow();
                }
            }
            Active active = active(c, supplied); VersionedSnapshot before = current(c, key);
            require(before.revision() == active.expectedRevision(), "Input state changed; restart the task");
            require(before.snapshot().status() == BoardStatus.PROCESSING, "Board is not processing");
            BoardState state = new BoardState(before.snapshot()); Task task = state.task(supplied.taskId());
            state.replace(BoardState.transition(task, status, task.agentId()));
            state.recordExecution(new ExecutionRecord(task.id(), task.reason(), status, active.lease().startedAt(),
                    clock.instant(), summary, status == TaskStatus.FAILED
                    ? Optional.of(new Failure(FailureKind.AGENT, summary, false)) : Optional.empty()));
            long revision = before.revision() + 1;
            execute(c, "DELETE FROM leases WHERE token=?", supplied.token());
            execute(c, "INSERT INTO receipts VALUES (?, ?, ?, ?)", supplied.token(), request, revision, key);
            return commit(c, key, revision, state.snapshot());
        });
    }

    /** Expired attempts are fenced before safe requeueing. Uncertain side effects become FAILED. */
    public int recoverExpired(BoardId id) {
        return transaction(c -> {
            String key = boardKey(id); VersionedSnapshot before = current(c, key);
            BoardState state = new BoardState(before.snapshot()); List<TaskLease> expired = new ArrayList<>();
            try (PreparedStatement p = prepare(c, "SELECT payload FROM leases WHERE board_key=?", key); ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    TaskLease lease = JsonCodec.read(r.getString(1), TaskLease.class);
                    if (!lease.expiresAt().isAfter(clock.instant())) expired.add(lease);
                }
            }
            for (TaskLease lease : expired) {
                Task task = state.task(lease.taskId());
                boolean retry = lease.recoveryPolicy() == RecoveryPolicy.RETRY_SAFE;
                state.replace(BoardState.transition(task, retry ? TaskStatus.PENDING : TaskStatus.FAILED, task.agentId()));
                state.recordExecution(new ExecutionRecord(task.id(), task.reason(), TaskStatus.FAILED, lease.startedAt(), clock.instant(),
                        retry ? "Lease expired; safe task requeued" : "Lease expired; manual confirmation required before retry",
                        Optional.of(new Failure(FailureKind.INTERRUPTED, "Execution lease expired", retry))));
                execute(c, "DELETE FROM leases WHERE token=?", lease.token());
            }
            if (!expired.isEmpty()) commit(c, key, before.revision() + 1, state.snapshot());
            return expired.size();
        });
    }

    /** Explicitly release a stale attempt without waiting for lease expiry. */
    public void abandon(TaskLease supplied) {
        transaction(c -> {
            TaskLease lease = active(c, supplied).lease();
            String key = boardKey(lease.boardId()); VersionedSnapshot before = current(c, key);
            BoardState state = new BoardState(before.snapshot()); Task task = state.task(lease.taskId());
            boolean retry = lease.recoveryPolicy() == RecoveryPolicy.RETRY_SAFE;
            state.replace(BoardState.transition(task, retry ? TaskStatus.PENDING : TaskStatus.FAILED, task.agentId()));
            state.recordExecution(new ExecutionRecord(task.id(), task.reason(), TaskStatus.FAILED, lease.startedAt(),
                    clock.instant(), "Attempt abandoned; " + (retry ? "safe task requeued" : "manual confirmation required"),
                    Optional.of(new Failure(FailureKind.INTERRUPTED, "Attempt abandoned", retry))));
            execute(c, "DELETE FROM leases WHERE token=?", lease.token());
            commit(c, key, before.revision() + 1, state.snapshot()); return null;
        });
    }

    public ProjectionStatus projectionStatus(BoardId id) {
        try (Connection c = connection(); PreparedStatement p = prepare(c, "SELECT * FROM projections WHERE board_key=?", boardKey(id)); ResultSet r = p.executeQuery()) {
            require(r.next(), "Unknown board"); return new ProjectionStatus(r.getLong("desired"), r.getLong("acknowledged"));
        } catch (SQLException e) { throw new StorageException("Cannot read projection status", e); }
    }

    public List<BoardId> boards() {
        try (Connection c = connection(); PreparedStatement p = prepare(c, "SELECT s.payload FROM snapshots s JOIN boards b ON s.board_key=b.board_key AND s.revision=b.revision ORDER BY b.board_key"); ResultSet r = p.executeQuery()) {
            List<BoardId> ids = new ArrayList<>();
            while (r.next()) ids.add(JsonCodec.read(r.getString(1), BlackboardSnapshot.class).id());
            return List.copyOf(ids);
        } catch (SQLException e) { throw new StorageException("Cannot list boards", e); }
    }

    public void acknowledge(BoardId id, long revision) {
        transaction(c -> {
            require(read(c, boardKey(id), revision).isPresent(), "Unknown projection revision");
            execute(c, "UPDATE projections SET acknowledged=MAX(acknowledged, ?) WHERE board_key=?", revision, boardKey(id));
            return null;
        });
    }
}
