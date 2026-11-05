package org.supermas.blackboard.export;

import java.nio.file.Path;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;

/** Subprocess fixture for abrupt termination after Markdown publication. */
public final class MarkdownCrashProbe {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        SqliteBlackboardStore store = new SqliteBlackboardStore(root.resolve("state.db"));
        try (MarkdownProjector projector = new MarkdownProjector(store, root.resolve("super-mas-vault"), stage -> {
            if (stage == MarkdownProjector.Stage.AFTER_PUBLISH) Runtime.getRuntime().halt(71);
        })) { projector.reconcile(); }
    }
}
