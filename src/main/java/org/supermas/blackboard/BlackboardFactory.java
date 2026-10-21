package org.supermas.blackboard;

import org.supermas.model.Domain.*;

public interface BlackboardFactory {
    Blackboard create(Email email, Analysis analysis);
    Blackboard restore(BlackboardSnapshot snapshot);
}
