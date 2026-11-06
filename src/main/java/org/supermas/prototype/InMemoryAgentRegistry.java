package org.supermas.prototype;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.supermas.agent.AgentRegistry;
import org.supermas.agent.SpecializedAgent;

// PROTOTYPE-STAND-IN: owned by the team's agent registry work, not by the Metadata/Context agents.
// Required by these agents: registration by configuration alone, and context agents never being reply candidates.
// Replace with the real implementation.
/** Agents in registration order, so that routing candidates are deterministic. */
public final class InMemoryAgentRegistry implements AgentRegistry {
    private final Map<String, SpecializedAgent> agents = new LinkedHashMap<>();

    @Override
    public synchronized void register(SpecializedAgent agent) {
        String id = agent.config().id();
        if (agents.containsKey(id)) {
            throw new IllegalArgumentException("Agent " + id + " is already registered");
        }
        if (agent.config().fallback() && fallback().isPresent()) {
            throw new IllegalArgumentException("A fallback agent is already registered");
        }
        agents.put(id, agent);
    }

    @Override
    public synchronized void unregister(String agentId) {
        agents.remove(agentId);
    }

    @Override
    public synchronized Optional<SpecializedAgent> find(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    @Override
    public synchronized List<SpecializedAgent> all() {
        return List.copyOf(agents.values());
    }

    @Override
    public synchronized Optional<SpecializedAgent> fallback() {
        return agents.values().stream().filter(agent -> agent.config().fallback()).findFirst();
    }

    // ASSUMED-TEAM-DECISION(P4): reply candidates are filtered on AgentConfig.capabilities. Pending team validation.
    // If rejected: apply the team's way of separating agent kinds.
    /** Agents that may answer the email, leaving out context agents. */
    public synchronized List<SpecializedAgent> replyCandidates() {
        return agents.values().stream()
                .filter(agent -> agent.config().capabilities().contains(Capabilities.DRAFT_REPLY))
                .toList();
    }
}
