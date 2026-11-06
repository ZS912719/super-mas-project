package org.supermas.metadata;

/** Raised when a raw message cannot be parsed into an {@code Email} at all. */
public class EmailParseException extends Exception {
    public EmailParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
