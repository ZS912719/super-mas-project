package org.supermas.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.Scope;

class ScopeTest {
    @Test
    void equalThreadIdsInDifferentProjectsAreDifferentScopes() {
        assertNotEquals(new Scope("project-a", "thread-1"), new Scope("project-b", "thread-1"));
    }

    @Test
    void equalProjectAndThreadIdsAreTheSameScope() {
        assertEquals(new Scope("project-a", "thread-1"), new Scope("project-a", "thread-1"));
    }
}
