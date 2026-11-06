package org.supermas.blackboard.testing;

import org.junit.jupiter.api.*;
import org.supermas.model.Domain.*;
import org.supermas.blackboard.model.BlackboardData.*;
import org.supermas.blackboard.impl.DefaultBlackboard;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.io.TempDir;

public abstract class BlackboardTestSupport {
    @TempDir protected Path root;
    protected MutableClock clock;
    protected SqliteBlackboardStore store;
    protected DefaultBlackboard board;
    protected BoardId id;
    protected AgentConfig agent;

    @BeforeEach protected void prepare() {
        clock = new MutableClock(); store = new SqliteBlackboardStore(root.resolve("state.db"), clock);
        Scope scope = new Scope("project", "thread"); id = new BoardId(scope, "board");
        Email email = new Email("mail", scope, "sender", List.of("recipient"), "Subject", "Untrusted ``` content", clock.instant(), Map.of());
        board = new DefaultBlackboardFactory(store).create(id, email, new Analysis(List.of(), List.of(), Map.of()));
        board.enqueue(task("work", id));
        agent = agent("agent");
    }

    public static AgentConfig agent(String name) { return new AgentConfig(name, "role", "Trusted system instructions", Set.of(), List.of(), Set.of("source"), false); }
    protected static Task task(String name, BoardId id) { return new Task(name, id, Action.GENERATE, Optional.empty(), TaskStatus.PENDING, "Task goal"); }
    protected TaskLease claim(RecoveryPolicy policy) { return board.claim("work", agent, Duration.ofSeconds(10), policy); }


    protected static Evidence evidence(String name, Scope scope, Set<String> agents) {
        return new Evidence(name, scope, EvidenceKind.RETRIEVED_EVIDENCE,
                new Provenance("source", "Source", Instant.EPOCH), "Evidence", agents);
    }

    protected Process probe(Class<?> probeClass, String mode, String name) throws IOException {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                probeClass.getName(), root.toString(), mode, name).redirectErrorStream(true).start();
    }

    public static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T12:00:00Z");
        public void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
