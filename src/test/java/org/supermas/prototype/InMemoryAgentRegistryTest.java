package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.supermas.prototype.Fixtures.agent;
import static org.supermas.prototype.Fixtures.idleAgent;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.agent.SpecializedAgent;

class InMemoryAgentRegistryTest {
    private final InMemoryAgentRegistry registry = new InMemoryAgentRegistry();

    private static SpecializedAgent agentNamed(String id, Set<String> capabilities, boolean fallback) {
        return idleAgent(agent(id, capabilities, Set.of(), fallback), 0);
    }

    @Test
    void contextAgentsAreRegisteredButAreNotReplyCandidates() {
        registry.register(agentNamed("context", Set.of("retrieve"), false));
        registry.register(agentNamed("student", Set.of(Capabilities.DRAFT_REPLY), false));
        assertEquals(2, registry.all().size());
        assertEquals(List.of("student"), registry.replyCandidates().stream().map(a -> a.config().id()).toList());
    }

    @Test
    void identifiersAndTheFallbackAreUnique() {
        registry.register(agentNamed("a", Set.of(), true));
        assertThrows(IllegalArgumentException.class, () -> registry.register(agentNamed("a", Set.of(), false)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(agentNamed("b", Set.of(), true)));
        assertEquals("a", registry.fallback().orElseThrow().config().id());
    }

    @Test
    void agentsCanBeAddedAndRemovedWhileTheOrderStaysStable() {
        registry.register(agentNamed("a", Set.of(), false));
        registry.register(agentNamed("b", Set.of(), false));
        registry.register(agentNamed("c", Set.of(), false));
        registry.unregister("b");
        assertTrue(registry.find("b").isEmpty());
        assertEquals(List.of("a", "c"), registry.all().stream().map(a -> a.config().id()).toList());
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
    }
}
