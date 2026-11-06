package org.supermas.blackboard.app;

import java.nio.file.Path;
import java.util.concurrent.*;

import org.supermas.blackboard.api.StateConflict;
import org.supermas.blackboard.export.MarkdownProjector;
import org.supermas.blackboard.persistence.BlackboardKeys;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;

/** Local observation/recovery process. It does not schedule or execute agents. */
public final class BlackboardObserver {
    private BlackboardObserver() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : "runtime");
        String mode = args.length > 1 ? args[1] : "audit";
        SqliteBlackboardStore store = new SqliteBlackboardStore(root.resolve("blackboard.db"));
        try (MarkdownProjector projector = new MarkdownProjector(store, root.resolve("super-mas-vault"))) {
            if (mode.equals("audit")) {
                boolean valid = true;
                for (var id : store.boards()) {
                    var audit = projector.audit(id);
                    System.out.println(BlackboardKeys.boardKey(id) + ": " + audit);
                    valid &= audit.valid() && audit.documentRevision() == audit.databaseRevision();
                }
                if (!valid) throw new StateConflict("One or more observations are invalid or lagging");
                return;
            }
            if (!mode.equals("watch")) throw new IllegalArgumentException("Expected audit or watch");
            ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::shutdownNow, "supermas-observer-shutdown"));
            worker.scheduleWithFixedDelay(() -> {
                try {
                    for (var id : store.boards()) store.recoverExpired(id);
                    projector.reconcile();
                } catch (Exception e) { System.err.println("Observation cycle failed; will retry: " + e); }
            }, 0, 1, TimeUnit.SECONDS);
            System.out.println("Watching SQLite state and regenerating Markdown. Press Ctrl+C to stop.");
            try { new CountDownLatch(1).await(); }
            finally { worker.shutdownNow(); worker.awaitTermination(5, TimeUnit.SECONDS); }
        }
    }
}
