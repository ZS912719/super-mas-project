package org.supermas.context;

import org.supermas.model.Domain.Email;

/**
 * Says whether an email's sender is really who it claims to be. This must rest on something an outside
 * sender cannot write, such as what the receiving mail system confirmed. Headers in the message are not
 * enough, and DMARC proves a domain and not a person.
 */
@FunctionalInterface
public interface SenderVerifier {
    boolean isVerified(Email email);

    /** Nobody is verified, which is the only safe default without a real signal. */
    static SenderVerifier none() {
        return email -> false;
    }
}
