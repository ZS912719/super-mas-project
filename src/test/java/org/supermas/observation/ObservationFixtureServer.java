package org.supermas.observation;

import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Synthetic-container test harness. Fault controls never modify the SQLite authority. */
public final class ObservationFixtureServer {
    private ObservationFixtureServer() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: ObservationFixtureServer <database> <controls-directory>");
        Path controls = Path.of(args[1]);
        ObservationSource delegate = new SqliteObservationSource(Path.of(args[0]),
                Set.of(System.getenv("SUPERMAS_OBSERVATION_BOARDS").split(",")), Clock.systemUTC());
        ObservationSource source = new ObservationSource() {
            private void checkAvailable() {
                if (Files.exists(controls.resolve("unavailable"))) throw new IllegalStateException("Controlled outage");
            }
            public Set<String> authorizedBoards() { return delegate.authorizedBoards(); }
            public List<String> boards() {
                checkAvailable();
                return Files.exists(controls.resolve("empty-discovery")) ? List.of() : delegate.boards();
            }
            public Capture capture(String board) {
                checkAvailable();
                Capture actual = delegate.capture(board);
                Path offset = controls.resolve("age-seconds");
                try {
                    long age = Files.exists(offset) ? Long.parseLong(Files.readString(offset).trim()) : 0;
                    if (age < 0 || age > 3600) throw new IllegalArgumentException("Invalid fixture age");
                    return new Capture(actual.databaseId(), actual.board(), actual.observedAt().minusSeconds(age),
                            actual.history(), actual.historyTruncated(), actual.attempts());
                } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read fault control", e); }
            }
        };
        ObservationServer server = new ObservationServer(source, System.getenv("SUPERMAS_OBSERVATION_TOKEN"), 8091, true);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("Synthetic read-only observation listening on 127.0.0.1:8091");
        new CountDownLatch(1).await();
    }
}
