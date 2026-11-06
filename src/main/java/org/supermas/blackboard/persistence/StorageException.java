package org.supermas.blackboard.persistence;



/** A persistence failure; callers must not assume a command was committed. */
public final class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
