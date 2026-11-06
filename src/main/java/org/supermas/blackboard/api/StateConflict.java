package org.supermas.blackboard.api;



/** The requested transition is stale, unauthorized or invalid for the current state. */
public final class StateConflict extends IllegalStateException {
    public StateConflict(String message) { super(message); }
}
