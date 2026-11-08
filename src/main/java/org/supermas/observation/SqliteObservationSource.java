package org.supermas.observation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.supermas.blackboard.model.BlackboardData.TaskLease;
import org.supermas.blackboard.persistence.codec.JsonCodec;
import org.supermas.model.Domain.BlackboardSnapshot;
import static org.supermas.blackboard.persistence.BlackboardKeys.boardKey;

/** Short, bounded read transactions. Decoding and HTTP serialization happen after closing SQLite. */
public final class SqliteObservationSource implements ObservationSource {
    private static final int HISTORY_LIMIT = 128;
    private static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;
    private static final int MAX_CAPTURE_BYTES = 16 * 1024 * 1024;
    private final String url;
    private final Set<String> allowed;
    private final Clock clock;

    public SqliteObservationSource(Path database, Set<String> allowed, Clock clock) {
        if (!Files.isRegularFile(database)) throw new IllegalArgumentException("An existing database is required");
        if (allowed.isEmpty() || allowed.size() > 100 || allowed.stream().anyMatch(s -> !s.matches("[a-f0-9]{64}")))
            throw new IllegalArgumentException("Authorize between 1 and 100 exact board keys");
        this.allowed = Set.copyOf(allowed);
        this.clock = clock;
        url = "jdbc:sqlite:" + database.toAbsolutePath().normalize().toUri().toASCIIString() + "?mode=ro";
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only=ON");
            statement.execute("PRAGMA busy_timeout=1000");
            try (ResultSet schema = statement.executeQuery("PRAGMA user_version")) {
                if (!schema.next() || schema.getInt(1) != 1) throw new SQLException("Unsupported observation schema");
            }
        } catch (SQLException e) { connection.close(); throw e; }
        return connection;
    }

    @Override public Set<String> authorizedBoards() { return allowed; }

    @Override public List<String> boards() {
        try (Connection connection = connect(); PreparedStatement query = connection.prepareStatement(
                "SELECT board_key FROM boards WHERE board_key=?")) {
            List<String> result = new ArrayList<>();
            for (String board : new TreeSet<>(allowed)) {
                query.setString(1, board);
                try (ResultSet rows = query.executeQuery()) { if (rows.next()) result.add(board); }
            }
            return List.copyOf(result);
        } catch (SQLException e) { throw new IllegalStateException("Observation unavailable", e); }
    }

    private record Raw(long revision, String at, String payload) { }

    @Override public Capture capture(String board) {
        if (!allowed.contains(board)) throw new NoSuchElementException("Board unavailable");
        List<Raw> history = new ArrayList<>();
        List<String> attempts = new ArrayList<>();
        String databaseId;
        long latest;
        int bytes = 0;
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(
                    "SELECT value FROM metadata WHERE name='database_id'")) {
                if (!rows.next()) throw new IllegalStateException("Database identity missing");
                databaseId = rows.getString(1);
            }
            try (PreparedStatement query = connection.prepareStatement("SELECT revision FROM boards WHERE board_key=?")) {
                query.setString(1, board);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new NoSuchElementException("Board unavailable");
                    latest = rows.getLong(1);
                }
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT revision, committed_at, payload, length(CAST(payload AS BLOB)) FROM snapshots "
                    + "WHERE board_key=? AND revision<=? ORDER BY revision DESC LIMIT ?")) {
                query.setString(1, board); query.setLong(2, latest); query.setInt(3, HISTORY_LIMIT);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        int size = rows.getInt(4);
                        bytes += size;
                        if (size > MAX_PAYLOAD_BYTES || bytes > MAX_CAPTURE_BYTES)
                            throw new IllegalStateException("Observation capture exceeds size limit");
                        history.add(new Raw(rows.getLong(1), rows.getString(2), rows.getString(3)));
                    }
                }
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT payload, length(CAST(payload AS BLOB)) FROM attempts INDEXED BY attempts_by_board_revision WHERE board_key=? AND input_revision<=? "
                    + "ORDER BY input_revision LIMIT 1001")) {
                query.setString(1, board); query.setLong(2, latest);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        bytes += rows.getInt(2);
                        if (rows.getInt(2) > MAX_PAYLOAD_BYTES || bytes > MAX_CAPTURE_BYTES || attempts.size() == 1000)
                            throw new IllegalStateException("Observation attempts exceed size limit");
                        attempts.add(rows.getString(1));
                    }
                }
            }
            connection.rollback();
        } catch (SQLException e) { throw new IllegalStateException("Observation unavailable", e); }
        Collections.reverse(history);
        List<Revision> revisions = history.stream().map(raw -> new Revision(raw.revision(), Instant.parse(raw.at()),
                JsonCodec.read(raw.payload(), BlackboardSnapshot.class))).toList();
        if (revisions.isEmpty() || revisions.get(revisions.size() - 1).revision() != latest)
            throw new IllegalStateException("Current snapshot missing");
        if (revisions.stream().anyMatch(revision -> !boardKey(revision.snapshot().id()).equals(board)))
            throw new IllegalStateException("Snapshot identity mismatch");
        List<Attempt> safeAttempts = attempts.stream().map(raw -> JsonCodec.read(raw, TaskLease.class))
                .peek(lease -> {
                    if (!boardKey(lease.boardId()).equals(board)) throw new IllegalStateException("Attempt identity mismatch");
                })
                .map(lease -> new Attempt(JsonCodec.hash(lease.token()), lease.taskId(), lease.agent().id(),
                        lease.startedAt(), lease.inputRevision())).toList();
        return new Capture(databaseId, board, clock.instant(), revisions, revisions.get(0).revision() > 1, safeAttempts);
    }
}
