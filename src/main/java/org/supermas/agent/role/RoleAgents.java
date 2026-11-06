package org.supermas.agent.role;

import java.util.List;
import org.supermas.model.Domain.AgentConfig;

/** Builds the agents of a catalogue. Registering them with the team's registry is all the coordinator needs. */
public final class RoleAgents {
    private RoleAgents() { }

    public static List<RoleAgent> create(RoleCatalog catalog, RoleServices services) {
        return catalog.roles().stream().map(role -> create(catalog, role.id(), services)).toList();
    }

    public static RoleAgent create(RoleCatalog catalog, String roleId, RoleServices services) {
        RoleDefinition role = catalog.role(roleId).orElseThrow(() -> new IllegalArgumentException("Unknown role " + roleId));
        AgentConfig config = catalog.config(roleId);
        String version = catalog.promptVersion(roleId);
        return switch (role.kind()) {
            case "student" -> new RoleAgent.Student(role, config, version, services);
            case "teacher" -> new RoleAgent.Teacher(role, config, version, services);
            case "research" -> new RoleAgent.Research(role, config, version, services);
            case "administration" -> new RoleAgent.Administration(role, config, version, services);
            case "generic" -> new RoleAgent.Generic(role, config, version, services);
            default -> new RoleAgent(role, config, version, services);
        };
    }
}
