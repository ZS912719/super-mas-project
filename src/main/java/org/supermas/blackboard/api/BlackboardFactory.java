package org.supermas.blackboard.api;

import org.supermas.model.Domain.*;

public interface BlackboardFactory {
    Blackboard create(Email email, Analysis analysis);
    Blackboard restore(BlackboardSnapshot snapshot);
}
