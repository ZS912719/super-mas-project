package org.supermas.agent;

import java.util.List;
import java.util.Optional;

/** Extension point for registration without coordinator-specific classification branches. */
public interface AgentRegistry {
    void register(SpecializedAgent agent);
    void unregister(String agentId);
    Optional<SpecializedAgent> find(String agentId);
    List<SpecializedAgent> all();
    Optional<SpecializedAgent> fallback();
}
