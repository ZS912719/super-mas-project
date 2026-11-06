package org.supermas.context;

import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;

/** Decides what an email needs but does not state. Must not fail: an unreadable analysis means nothing missing. */
public interface MissingInformationCheck {
    MissingInformation check(Email email, Analysis analysis);
}
