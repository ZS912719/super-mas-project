package org.supermas.prototype;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/** Shared test data for the stand-ins. */
final class Fixtures {
    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private Fixtures() { }

    static Email email(Scope scope, String body) {
        return new Email("mail-1", scope, "a@b.c", List.of("d@e.f"), "Subject", body, NOW, Map.of());
    }

    static Analysis analysis() {
        return new Analysis(List.of(), List.of(), Map.of());
    }

    static AgentConfig agent(String id, Set<String> capabilities, Set<String> sources, boolean fallback) {
        return new AgentConfig(id, "role", "prompt", capabilities, List.of(), sources, fallback);
    }

    static Evidence evidence(String id, Scope scope, EvidenceKind kind, String source, Set<String> permitted) {
        return new Evidence(id, scope, kind, new Provenance(source, "test", NOW), "text", permitted);
    }

    /** An agent that only reports its configuration and a fixed score. */
    static SpecializedAgent idleAgent(AgentConfig config, double score) {
        return new SpecializedAgent() {
            @Override
            public AgentConfig config() {
                return config;
            }

            @Override
            public AgentAssessment assess(Task task, BlackboardView board) {
                return new AgentAssessment(config.id(), score, true, "fixed score");
            }

            @Override
            public AgentOutcome execute(Task task, AgentBlackboard board) {
                return new AgentOutcome(TaskStatus.SUCCEEDED, "nothing to do", List.of(), Optional.empty());
            }
        };
    }
}
