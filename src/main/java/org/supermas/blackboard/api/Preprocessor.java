package org.supermas.blackboard.api;

import org.supermas.model.Domain.*;

/** Email content is untrusted data. Initial labels are hints rather than verified facts. */
public interface Preprocessor {
    Analysis analyze(Email email);
}
