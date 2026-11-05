package org.supermas.blackboard.impl;

import java.nio.file.Path;
import java.time.Duration;
import org.supermas.blackboard.api.StateConflict;
import org.supermas.blackboard.model.BlackboardData.RecoveryPolicy;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;
import org.supermas.blackboard.testing.BlackboardTestSupport;
import org.supermas.model.Domain.*;

/** Subprocess fixture for OS-level SQLite claim contention. */
public final class ClaimProcessProbe {
    public static void main(String[] args) {
        SqliteBlackboardStore store = new SqliteBlackboardStore(Path.of(args[0]).resolve("state.db"));
        BoardId id = new BoardId(new Scope("project", "thread"), "board");
        try {
            store.claim(id, "work", BlackboardTestSupport.agent(args[2]), Duration.ofSeconds(10), RecoveryPolicy.RETRY_SAFE);
            System.out.println("CLAIMED");
        } catch (StateConflict e) { System.out.println("CONFLICT"); }
    }
}
