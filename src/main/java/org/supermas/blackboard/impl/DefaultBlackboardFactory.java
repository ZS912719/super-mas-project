package org.supermas.blackboard.impl;

import java.util.*;

import org.supermas.blackboard.api.BlackboardFactory;
import org.supermas.blackboard.persistence.BlackboardStore;
import org.supermas.model.Domain.*;

public final class DefaultBlackboardFactory implements BlackboardFactory {
    private final BlackboardStore store;
    public DefaultBlackboardFactory(BlackboardStore store) { this.store = Objects.requireNonNull(store); }

    @Override public DefaultBlackboard create(Email email, Analysis analysis) {
        return create(new BoardId(email.scope(), UUID.randomUUID().toString()), email, analysis);
    }

    public DefaultBlackboard create(BoardId id, Email email, Analysis analysis) {
        store.save(new BlackboardSnapshot(id, email, analysis, List.of(), List.of(), List.of(), List.of(),
                List.of(), Optional.empty(), BoardStatus.PROCESSING, new BudgetUsage(0, 0, 0), Optional.empty()));
        return open(id);
    }

    public DefaultBlackboard open(BoardId id) { return new DefaultBlackboard(store, id); }

    /** Restore is an import into a new identity, never a blind replacement of an existing board. */
    @Override public DefaultBlackboard restore(BlackboardSnapshot snapshot) {
        store.save(snapshot); return open(snapshot.id());
    }
}
