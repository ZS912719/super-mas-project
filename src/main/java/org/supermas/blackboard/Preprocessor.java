package org.supermas.blackboard;

import org.supermas.model.Domain.*;

/** Email content is untrusted data. Initial labels are hints rather than verified facts. */
public interface Preprocessor {
    Analysis analyze(Email email);
}
