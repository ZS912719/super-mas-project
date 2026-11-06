package org.supermas.blackboard.impl;

import org.junit.jupiter.api.*;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.model.Domain.*;
import org.supermas.blackboard.model.BlackboardData.*;
import org.supermas.blackboard.api.StateConflict;
import org.supermas.blackboard.persistence.BlackboardKeys;
import org.supermas.blackboard.persistence.StorageException;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;
import org.supermas.blackboard.persistence.codec.JsonCodec;
import org.supermas.blackboard.testing.BlackboardTestSupport;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BlackboardTest extends BlackboardTestSupport {
    @Test void concurrentConnectionsHaveOnlyOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String name = "agent-" + i;
                results.add(pool.submit(() -> {
                    DefaultBlackboard other = new DefaultBlackboardFactory(new SqliteBlackboardStore(root.resolve("state.db"), clock)).open(id);
                    start.await();
                    try { other.claim("work", agent(name), Duration.ofSeconds(10), RecoveryPolicy.RETRY_SAFE); return true; }
                    catch (StateConflict e) { return false; }
                }));
            }
            start.countDown(); int winners = 0;
            for (Future<Boolean> future : results) if (future.get(15, TimeUnit.SECONDS)) winners++;
            assertEquals(1, winners); assertEquals(TaskStatus.RUNNING, board.snapshot().tasks().get(0).status());
        } finally { pool.shutdownNow(); }
    }

    @Test void expiryAndRestartFenceLateCompletion() {
        TaskLease old = claim(RecoveryPolicy.RETRY_SAFE); clock.advance(11);
        DefaultBlackboard reopened = new DefaultBlackboardFactory(new SqliteBlackboardStore(root.resolve("state.db"), clock)).open(id);
        assertEquals(1, reopened.recoverExpired());
        TaskLease fresh = reopened.claim("work", agent, Duration.ofSeconds(10), RecoveryPolicy.RETRY_SAFE);
        assertNotEquals(old.token(), fresh.token());
        assertThrows(StateConflict.class, () -> reopened.complete(old, TaskStatus.SUCCEEDED, "late"));
        reopened.complete(fresh, TaskStatus.SUCCEEDED, "done");
        assertEquals(2, reopened.snapshot().executions().size());
    }

    @Test void uncertainExecutionRequiresManualConfirmation() {
        claim(RecoveryPolicy.REQUIRE_CONFIRMATION); clock.advance(11); board.recoverExpired();
        assertEquals(TaskStatus.FAILED, board.snapshot().tasks().get(0).status());
        assertFalse(board.snapshot().executions().get(0).failure().orElseThrow().retryable());
        assertThrows(StateConflict.class, () -> claim(RecoveryPolicy.RETRY_SAFE));
    }

    @Test void exactCompletionRetryIsIdempotent() {
        TaskLease lease = claim(RecoveryPolicy.RETRY_SAFE);
        VersionedSnapshot result = board.complete(lease, TaskStatus.SUCCEEDED, "done");
        assertEquals(result.revision(), board.complete(lease, TaskStatus.SUCCEEDED, "done").revision());
        assertThrows(StateConflict.class, () -> board.complete(lease, TaskStatus.SUCCEEDED, "different"));
        assertEquals(1, board.snapshot().executions().size());
        assertEquals(agent.systemPrompt(), board.versionedSnapshot().attempts().get(0).agent().systemPrompt());
        assertTrue(store.version(id, 2).orElseThrow().attempts().isEmpty());
    }

    @Test void unrelatedMutationRejectsStaleInputAndAllowsExplicitAbandon() {
        TaskLease lease = claim(RecoveryPolicy.RETRY_SAFE);
        board.recordBudgetUsage(new BudgetUsage(1, 0, 0));
        assertThrows(StateConflict.class, () -> board.complete(lease, TaskStatus.SUCCEEDED, "stale"));
        assertThrows(StateConflict.class, () -> board.heartbeat(lease, Duration.ofSeconds(10)));
        board.abandon(lease);
        TaskLease next = claim(RecoveryPolicy.RETRY_SAFE); board.complete(next, TaskStatus.SUCCEEDED, "fresh");
    }

    @Test void heartbeatDoesNotInvalidateInputAndPromptIsFrozen() {
        TaskLease lease = claim(RecoveryPolicy.RETRY_SAFE); long revision = board.versionedSnapshot().revision();
        clock.advance(5); TaskLease renewed = board.heartbeat(lease, Duration.ofSeconds(10));
        assertEquals(revision, board.versionedSnapshot().revision());
        assertEquals(JsonCodec.hash(agent.systemPrompt()), renewed.promptHash());
        clock.advance(6); board.complete(lease, TaskStatus.SUCCEEDED, "uses persisted renewal");
    }

    @Test void legacyCommandsCannotBypassExecutionOrApproval() {
        assertThrows(StateConflict.class, () -> board.accessFor(agent));
        assertThrows(StateConflict.class, () -> board.updateTask(new Task("work", id, Action.GENERATE,
                Optional.of(agent.id()), TaskStatus.SUCCEEDED, "bypass")));
        assertThrows(StateConflict.class, () -> board.setStatus(BoardStatus.APPROVED));
        assertThrows(StateConflict.class, () -> store.save(board.snapshot()));
    }

    @Test void failedBatchRollsBackSnapshotAndOutbox() {
        TaskLease lease = claim(RecoveryPolicy.RETRY_SAFE); AgentBlackboard view = board.accessFor(lease);
        long before = board.versionedSnapshot().revision();
        Evidence valid = evidence("good", id.scope(), Set.of(agent.id()));
        Evidence foreign = evidence("bad", new Scope("foreign", "thread"), Set.of(agent.id()));
        assertThrows(StateConflict.class, () -> view.submitEvidence(List.of(valid, foreign)));
        assertEquals(before, board.versionedSnapshot().revision()); assertTrue(board.snapshot().evidence().isEmpty());
        assertEquals(before, store.projectionStatus(id).desiredRevision());
    }

    @Test void agentCannotForgeAuthorOrSourcePermissions() {
        TaskLease lease = claim(RecoveryPolicy.RETRY_SAFE); AgentBlackboard view = board.accessFor(lease);
        assertThrows(StateConflict.class, () -> view.submitDraft(new DraftProposal("s", "b", "other-agent", "x")));
        assertThrows(StateConflict.class, () -> view.submitEvidence(List.of(evidence("x", id.scope(), Set.of("other")))));
        view.submitEvidence(List.of(evidence("own", id.scope(), Set.of(agent.id()))));
        view.submitDraft(new DraftProposal("s", "b", agent.id(), "x"));
        board.complete(lease, TaskStatus.SUCCEEDED, "done");
        assertEquals(1, board.snapshot().drafts().size());
        assertThrows(StateConflict.class, view::snapshot);
    }

    @Test void restrictedViewHidesOtherAgentsEvidence() {
        board.addEvidence(List.of(evidence("private", id.scope(), Set.of("other")), evidence("visible", id.scope(), Set.of(agent.id()))));
        AgentBlackboard view = board.accessFor(claim(RecoveryPolicy.RETRY_SAFE));
        assertEquals(List.of("visible"), view.snapshot().evidence().stream().map(Evidence::id).toList());
        BlackboardSnapshot detached = board.snapshot(); detached.email().metadata().put("mutated", "value");
        assertFalse(board.snapshot().email().metadata().containsKey("mutated"));
    }

    @Test void projectAndThreadIdentifiersRemainIsolated() {
        BoardId otherId = new BoardId(new Scope("another-project", "thread"), "board");
        Email otherMail = new Email("mail", otherId.scope(), "sender", List.of(), "s", "b", clock.instant(), Map.of());
        DefaultBlackboard other = new DefaultBlackboardFactory(store).create(otherId, otherMail, board.snapshot().analysis());
        assertNotEquals(BlackboardKeys.boardKey(id), BlackboardKeys.boardKey(otherId));
        assertThrows(StateConflict.class, () -> other.enqueue(task("bad", id)));
        assertTrue(other.snapshot().tasks().isEmpty());
    }

    @Test void sqliteBusyIsBoundedAndDoesNotPartiallyCommit() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("state.db")); Statement s = c.createStatement()) {
            s.execute("BEGIN IMMEDIATE"); long revision = board.versionedSnapshot().revision();
            assertThrows(StorageException.class, () -> board.enqueue(task("blocked", id)));
            s.execute("ROLLBACK"); assertEquals(revision, board.versionedSnapshot().revision());
        }
    }

    @Test void separateJvmProcessesContendForTheSameTask() throws Exception {
        // Use a real-time database because the subprocesses use the system clock.
        List<Process> processes = new ArrayList<>();
        for (String name : List.of("a", "b")) processes.add(probe(ClaimProcessProbe.class, "claim", name));
        int winners = 0;
        for (Process process : processes) {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            if (output.contains("CLAIMED")) winners++;
        }
        assertEquals(1, winners);
    }
}
