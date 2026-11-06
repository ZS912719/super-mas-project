package org.supermas.prototype;

import java.time.Clock;
import java.util.UUID;
import org.supermas.blackboard.api.Blackboard;
import org.supermas.blackboard.api.BlackboardFactory;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.Email;

// PROTOTYPE-STAND-IN: owned by the team's blackboard work, not by the Metadata/Context agents.
// Required by these agents: a new board with a unique task identifier for each received email.
// Replace with the real implementation.
/** Creates in-memory boards. Nothing outlives the process. */
public final class InMemoryBlackboardFactory implements BlackboardFactory {
    private final Clock clock;

    public InMemoryBlackboardFactory(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Blackboard create(Email email, Analysis analysis) {
        return new InMemoryBlackboard(new BoardId(email.scope(), UUID.randomUUID().toString()), email, analysis, clock);
    }

    @Override
    public Blackboard restore(BlackboardSnapshot snapshot) {
        return InMemoryBlackboard.restore(snapshot, clock);
    }
}
