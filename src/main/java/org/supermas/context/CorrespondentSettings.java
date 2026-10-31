package org.supermas.context;

/** The switch for correspondent topics. Off unless deliberately turned on with a real verifier. */
public record CorrespondentSettings(boolean enabled, SenderVerifier verifier) {
    public static CorrespondentSettings off() {
        return new CorrespondentSettings(false, SenderVerifier.none());
    }
}
