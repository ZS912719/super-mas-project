package org.supermas.persistence;

import org.supermas.model.Domain.AgentConfig;
import java.util.List;
import java.util.Optional;

/** Persist trusted English role prompts, declared capabilities and authorized context source identifiers. */
public interface AgentConfigRepository {
    void save(AgentConfig config);
    Optional<AgentConfig> find(String agentId);
    List<AgentConfig> all();
}
