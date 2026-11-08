package org.supermas.observation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.blackboard.model.BlackboardData.*;
import org.supermas.blackboard.persistence.codec.JsonCodec;
import org.supermas.blackboard.testing.BlackboardTestSupport;
import org.supermas.control.ControlEntry;
import org.supermas.model.Domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.supermas.blackboard.persistence.BlackboardKeys.boardKey;

class ObservationTest extends BlackboardTestSupport {
    private static final String TOKEN = "synthetic-observer-token-with-at-least-32-characters";
    private static final ObjectMapper JSON = new ObjectMapper();

    private SqliteObservationSource source() {
        return new SqliteObservationSource(root.resolve("state.db"), Set.of(boardKey(id)), clock);
    }
    private JsonNode view() throws Exception {
        return JSON.readTree(JsonCodec.write(new ObservationView(source().capture(boardKey(id)), false)
                .render("*", Instant.EPOCH, Instant.parse("2100-01-01T00:00:00Z"))));
    }
    private String authority() throws Exception {
        StringBuilder content = new StringBuilder();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("state.db"))) {
            for (String table : List.of("metadata", "boards", "snapshots", "attempts", "leases", "receipts", "projections")) {
                try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY 1, 2")) {
                    while (rows.next()) {
                        for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++)
                            content.append(rows.getString(column)).append('\n');
                    }
                }
            }
        }
        return JsonCodec.hash(content.toString());
    }
    private HttpResponse<String> request(ObservationServer server, String path, String token, String method) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .header("Authorization", "Bearer " + token).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test void repeatedPollingNeverRecoversAnExpiredLeaseOrChangesAnyAuthorityTable() throws Exception {
        TaskLease lease = claim(RecoveryPolicy.RETRY_SAFE);
        clock.advance(20);
        String before = authority();
        try (ObservationServer server = new ObservationServer(source(), TOKEN, 0, false)) {
            server.start();
            for (int index = 0; index < 12; index++) {
                HttpResponse<String> response = request(server, "/view?board=" + boardKey(id), TOKEN, "GET");
                assertEquals(200, response.statusCode(), response.body());
                assertEquals("RUNNING_RECORDED", JSON.readTree(response.body()).path("experts").get(0).path("currentExecution").asText());
                assertFalse(response.body().contains(lease.token()));
                assertFalse(response.body().contains(agent.systemPrompt()));
                assertFalse(response.body().contains(board.snapshot().email().body()));
            }
        }
        assertEquals(before, authority());
        assertTrue(board.snapshot().executions().isEmpty());
    }

    @Test void recoveryRetryAndCompletionAreSeparateAttemptsWithExactDurations() throws Exception {
        TaskLease first = claim(RecoveryPolicy.RETRY_SAFE);
        clock.advance(12);
        assertEquals(1, board.recoverExpired());
        TaskLease second = claim(RecoveryPolicy.RETRY_SAFE);
        clock.advance(2);
        board.accessFor(second).submitDraft(new DraftProposal("private subject", "private draft", agent.id(), "private explanation"));
        board.complete(second, TaskStatus.SUCCEEDED, "success");
        JsonNode observed = view();
        assertEquals(2, observed.path("events").size());
        assertEquals("FAILED", observed.path("events").get(0).path("result").asText());
        assertEquals("INTERRUPTED", observed.path("events").get(0).path("failureKind").asText());
        assertEquals(12000, observed.path("events").get(0).path("durationMs").asInt());
        assertEquals(JsonCodec.hash(first.token()), observed.path("events").get(0).path("attempt").asText());
        assertEquals(JsonCodec.hash(second.token()), observed.path("events").get(1).path("attempt").asText());
        assertEquals("UNKNOWN", observed.path("events").get(1).path("modelCallResult").asText());
        assertEquals("UNKNOWN", observed.path("experts").get(0).path("currentExecution").asText());
        assertEquals(2, observed.path("timeline").size());
        assertFalse(observed.toString().contains("private draft"));
    }

    @Test void cancellationAndAbandonmentAreRecordedWithoutInventingTerminalIntervals() throws Exception {
        board.updateTask(new Task("work", id, Action.GENERATE, Optional.of("agent"), TaskStatus.CANCELLED, "cancel"));
        board.enqueue(new Task("retry", id, Action.GENERATE, Optional.of("agent"), TaskStatus.PENDING, "retry"));
        TaskLease lease = board.claim("retry", agent, Duration.ofSeconds(30), RecoveryPolicy.RETRY_SAFE);
        clock.advance(1); board.abandon(lease);
        JsonNode observed = view();
        assertTrue(observed.path("events").toString().contains("CANCELLED"));
        assertTrue(observed.path("events").toString().contains("INTERRUPTED"));
        assertTrue(observed.path("timeline").findValuesAsText("state").stream().allMatch(value -> value.equals("RUNNING") || value.equals("PENDING")));
        assertEquals("PENDING", observed.path("experts").get(0).path("currentExecution").asText());
    }

    @Test void exactScopeAuthorizationDoesNotLeakOtherBoardsWithTheSameTaskId() throws Exception {
        Scope otherScope = new Scope("other-project", id.scope().threadId());
        BoardId other = new BoardId(otherScope, id.taskId());
        new DefaultBlackboardFactory(store).create(other,
                new Email("other-mail", otherScope, "private", List.of(), "private", "other-board-secret", clock.instant(), Map.of()),
                new Analysis(List.of(), List.of(), Map.of()));
        assertEquals(List.of(boardKey(id)), source().boards());
        assertThrows(NoSuchElementException.class, () -> source().capture(boardKey(other)));
        try (ObservationServer server = new ObservationServer(source(), TOKEN, 0, false)) {
            server.start();
            assertEquals(404, request(server, "/view?board=" + boardKey(other), TOKEN, "GET").statusCode());
            assertFalse(request(server, "/boards", TOKEN, "GET").body().contains(boardKey(other)));
        }
    }

    @Test void authenticationMutationAttemptsInvalidQueriesAndStorageFailureFailClosed() throws Exception {
        try (ObservationServer server = new ObservationServer(source(), TOKEN, 0, false)) {
            server.start();
            assertEquals(401, request(server, "/boards", "bad", "GET").statusCode());
            assertEquals(405, request(server, "/boards", TOKEN, "POST").statusCode());
            assertEquals(400, request(server, "/view?board=" + boardKey(id) + "&from=invalid", TOKEN, "GET").statusCode());
            assertEquals(400, request(server, "/view?board=" + boardKey(id) + "&board=" + boardKey(id), TOKEN, "GET").statusCode());
            assertEquals("no-store, max-age=0", request(server, "/boards", TOKEN, "GET").headers().firstValue("Cache-Control").orElseThrow());
        }
        ObservationSource unavailable = new ObservationSource() {
            public List<String> boards() { throw new IllegalStateException("credential-secret"); }
            public Capture capture(String key) { throw new IllegalStateException("credential-secret"); }
            public Set<String> authorizedBoards() { return Set.of(boardKey(id)); }
        };
        try (ObservationServer server = new ObservationServer(unavailable, TOKEN, 0, false)) {
            server.start();
            HttpResponse<String> response = request(server, "/view?board=" + boardKey(id), TOKEN, "GET");
            assertEquals(503, response.statusCode());
            assertFalse(JSON.readTree(response.body()).path("fresh").asBoolean(true));
            assertFalse(response.body().contains("credential-secret"));
        }
    }

    @Test void oldEligibilityIsUnknownAndNewEligibilityIsIndependentOfExecution() throws Exception {
        ControlEntry legacy = new ControlEntry(ControlEntry.SCHEMA, 1, "CONTRIBUTION", "CONTRIBUTE", "agent", 0.8,
                "sensitive reason", 0, 0, List.of(new ControlEntry.Rated("agent", "CONTRIBUTE", "CONTRIBUTION", 0.8, 0.8, "legacy")));
        board.addEvidence(List.of(legacy.toEvidence(id.scope(), clock.instant())));
        assertEquals("SCORED_ELIGIBILITY_UNKNOWN", view().path("experts").get(0).path("assessment").asText());
        ControlEntry modern = new ControlEntry(ControlEntry.SCHEMA, 2, "CONTRIBUTION", "CONTRIBUTE", "agent", 0.8,
                "sensitive reason", 0, 0, List.of(new ControlEntry.Rated("agent", "CONTRIBUTE", "CONTRIBUTION", 0.8, 0.8, "modern", true)));
        clock.advance(1); board.addEvidence(List.of(modern.toEvidence(id.scope(), clock.instant())));
        JsonNode observed = view();
        assertEquals("SUITABLE_AT_ASSESSMENT", observed.path("experts").get(0).path("assessment").asText());
        assertEquals("UNKNOWN", observed.path("experts").get(0).path("currentExecution").asText());
        assertFalse(observed.toString().contains("sensitive reason"));
        assertEquals(clock.instant().toEpochMilli(), observed.path("events").get(1).path("time").asLong());
        assertTrue(observed.path("events").get(1).path("eventTime").isNull());
    }

    @Test void boundedHistoryIsExplicitAndCurrentSnapshotRemainsAuthoritative() throws Exception {
        for (int index = 0; index < 130; index++) {
            clock.advance(1); board.recordBudgetUsage(new BudgetUsage(index + 1, 0, 0));
        }
        ObservationSource.Capture capture = source().capture(boardKey(id));
        assertEquals(128, capture.history().size());
        assertTrue(capture.historyTruncated());
        assertEquals(board.versionedSnapshot().revision(), capture.latest().revision());
        assertEquals(board.snapshot(), capture.latest().snapshot());
    }

    @Test void missingDatabaseIsNotCreatedAndNoAuthorizationWildcardIsAllowed() {
        Path missing = root.resolve("missing.db");
        assertThrows(IllegalArgumentException.class, () -> new SqliteObservationSource(missing, Set.of(boardKey(id)), clock));
        assertFalse(Files.exists(missing));
        assertThrows(IllegalArgumentException.class, () -> new SqliteObservationSource(root.resolve("state.db"), Set.of("*"), clock));
    }

    @Test void concurrentWritesNeverMixCapturedRevisionAndSnapshot() throws Exception {
        ExecutorService writer = Executors.newSingleThreadExecutor();
        try {
            Future<?> edits = writer.submit(() -> {
                for (int round = 1; round <= 24; round++) board.recordBudgetUsage(new BudgetUsage(round, 0, 0));
            });
            for (int index = 0; index < 24; index++) {
                ObservationSource.Capture capture = source().capture(boardKey(id));
                assertEquals(capture.latest().revision() - 2, capture.latest().snapshot().budgetUsage().rounds());
                assertEquals(store.version(id, capture.latest().revision()).orElseThrow().snapshot(), capture.latest().snapshot());
                assertTrue(capture.attempts().stream().allMatch(attempt -> attempt.inputRevision() <= capture.latest().revision()));
            }
            edits.get(10, TimeUnit.SECONDS);
        } finally { writer.shutdownNow(); }
    }

    @Test void oversizedAuthorityFailsWithoutReturningPartialOrSensitiveData() throws Exception {
        Scope scope = new Scope("large", "large");
        BoardId large = new BoardId(scope, "large");
        new DefaultBlackboardFactory(store).create(large,
                new Email("large", scope, "sender", List.of(), "subject", "x".repeat(4 * 1024 * 1024), clock.instant(), Map.of()),
                new Analysis(List.of(), List.of(), Map.of()));
        ObservationSource limited = new SqliteObservationSource(root.resolve("state.db"), Set.of(boardKey(large)), clock);
        try (ObservationServer server = new ObservationServer(limited, TOKEN, 0, false)) {
            server.start();
            HttpResponse<String> response = request(server, "/view?board=" + boardKey(large), TOKEN, "GET");
            assertEquals(503, response.statusCode());
            assertFalse(response.body().contains("overview"));
            assertTrue(response.body().length() < 200);
        }
    }

    @Test void attemptLookupUsesTheApplicationOwnedIndexAndObserverNeverCreatesIt() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("state.db"));
             Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("EXPLAIN QUERY PLAN SELECT payload FROM attempts "
                    + "INDEXED BY attempts_by_board_revision WHERE board_key='test' AND input_revision<=100 ORDER BY input_revision LIMIT 1001")) {
                assertTrue(rows.next());
                assertTrue(rows.getString("detail").contains("SEARCH attempts USING INDEX attempts_by_board_revision"));
            }
            statement.execute("DROP INDEX attempts_by_board_revision");
        }
        String before = authority();
        assertThrows(IllegalStateException.class, () -> source().capture(boardKey(id)));
        assertEquals(before, authority());
    }

    @Test void anAuthorizedButAbsentBoardIsSuccessfulEmptyDiscovery() throws Exception {
        ObservationSource absent = new SqliteObservationSource(root.resolve("state.db"), Set.of("0".repeat(64)), clock);
        try (ObservationServer server = new ObservationServer(absent, TOKEN, 0, false)) {
            server.start();
            HttpResponse<String> response = request(server, "/boards", TOKEN, "GET");
            assertEquals(200, response.statusCode());
            assertTrue(JSON.readTree(response.body()).path("boards").isEmpty());
            assertEquals(404, request(server, "/view?board=" + "0".repeat(64), TOKEN, "GET").statusCode());
        }
    }

    @Test void mixedPendingAndExecutionIntervalsAreChronologicalForTheNativeTimeline() throws Exception {
        board.enqueue(new Task("assigned", id, Action.GENERATE, Optional.of(agent.id()), TaskStatus.PENDING, "Controlled work"));
        clock.advance(1);
        TaskLease lease = board.claim("assigned", agent, Duration.ofSeconds(10), RecoveryPolicy.RETRY_SAFE);
        clock.advance(2);
        board.complete(lease, TaskStatus.SUCCEEDED, "done");
        long previous = Long.MIN_VALUE;
        Set<String> states = new HashSet<>();
        for (JsonNode interval : view().path("timeline")) {
            if (!agent.id().equals(interval.path("agent").asText())) continue;
            long start = interval.path("start").asLong();
            assertTrue(start >= previous, "Native timeline samples must be chronological");
            previous = start;
            states.add(interval.path("state").asText());
        }
        assertEquals(Set.of("PENDING", "RUNNING"), states);
    }

    @Test void leaseStartBeforeCommitKeepsBothStatesAndMarksTheOverlap() throws Exception {
        java.util.concurrent.atomic.AtomicLong millis = new java.util.concurrent.atomic.AtomicLong(clock.instant().toEpochMilli());
        Clock ticking = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(millis.getAndIncrement()); }
        };
        Path database = root.resolve("ticking.db");
        var tickingStore = new org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore(database, ticking);
        var controlled = new DefaultBlackboardFactory(tickingStore).create(id, board.snapshot().email(), board.snapshot().analysis());
        controlled.enqueue(new Task("assigned", id, Action.GENERATE, Optional.of(agent.id()), TaskStatus.PENDING, "Controlled work"));
        TaskLease lease = controlled.claim("assigned", agent, Duration.ofSeconds(10), RecoveryPolicy.RETRY_SAFE);
        controlled.complete(lease, TaskStatus.SUCCEEDED, "done");
        ObservationSource.Capture capture = new SqliteObservationSource(database, Set.of(boardKey(id)), ticking).capture(boardKey(id));
        JsonNode intervals = JSON.readTree(JsonCodec.write(new ObservationView(capture, false)
                .render("*", Instant.EPOCH, Instant.parse("2100-01-01T00:00:00Z")))).path("timeline");
        Set<String> states = new HashSet<>();
        long previousEnd = Long.MIN_VALUE;
        for (JsonNode interval : intervals) {
            assertTrue(interval.path("start").asLong() >= previousEnd, "Native interval ends must not erase another recorded state");
            previousEnd = interval.path("end").asLong();
            states.add(interval.path("state").asText());
        }
        assertEquals(Set.of("PENDING", "RUNNING", "OVERLAPPING_RECORDS"), states);
    }
}
