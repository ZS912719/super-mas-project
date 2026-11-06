package org.supermas.agent.role;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.context.InformationField;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Task;

/**
 * One specialized reply agent, entirely defined by its {@link RoleDefinition}. The same class serves every role,
 * so a new role needs a JSON file and a prompt file, and no code. {@link RoleAgents} gives each instance the role
 * interface (StudentAgent, TeacherAgent…) that matches its kind.
 *
 * <p>{@code assess} follows docs/plans/reply-agent-assess.md. {@code execute} writes a reply plan to the board,
 * then asks the generation service for a draft (steps 9.4 and 9.5).
 */
public class RoleAgent implements SpecializedAgent {
    static final double HINT_WEIGHT = 0.6;
    static final double KEYWORD_WEIGHT = 0.4;
    private static final int FULL_MATCH_KEYWORDS = 3;

    final RoleDefinition role;
    final AgentConfig config;
    final RoleServices services;
    final String promptVersion;

    RoleAgent(RoleDefinition role, AgentConfig config, String promptVersion, RoleServices services) {
        this.role = role;
        this.config = config;
        this.promptVersion = promptVersion;
        this.services = services;
    }

    public RoleDefinition role() {
        return role;
    }

    @Override
    public AgentConfig config() {
        return config;
    }

    @Override
    public AgentAssessment assess(Task task, BlackboardView board) {
        BlackboardSnapshot state = board.snapshot();
        Set<InformationField> missing = missingRequired(state);
        boolean sufficient = missing.isEmpty();
        if (role.fallback()) {
            return new AgentAssessment(config.id(), 0, sufficient, "Fallback role, chosen only when no role fits");
        }
        if (role.requiresVerifiedSender() && !verified(state)) {
            return new AgentAssessment(config.id(), 0, sufficient, "This role needs a verified sender");
        }
        Optional<String> hint = Optional.ofNullable(state.analysis().metadata().get(MetadataAgent.KEY_ROLE_HINT));
        boolean hinted = hint.filter(role.roleHints()::contains).isPresent();
        long matched = RoleText.matchedKeywords(role.keywords(), state);
        double keywordScore = role.keywords().isEmpty() ? 0
                : Math.min(1, (double) matched / Math.min(FULL_MATCH_KEYWORDS, role.keywords().size()));
        double score = Math.min(1, (hinted ? HINT_WEIGHT : 0) + KEYWORD_WEIGHT * keywordScore);
        return new AgentAssessment(config.id(), score, sufficient, (hinted ? "Role hint matches, " : "No matching role hint, ")
                + matched + " role keywords matched, " + missing.size() + " required fields reported missing");
    }

    @Override
    public AgentOutcome execute(Task task, AgentBlackboard board) {
        return ReplyWork.run(this, task, board);
    }

    /** Required fields of this role that the missing-information check, as this agent may read it, reports. */
    Set<InformationField> missingRequired(BlackboardSnapshot state) {
        Set<InformationField> missing = new TreeSet<>();
        for (Evidence item : state.evidence()) {
            if (item.kind() == EvidenceKind.MODEL_INFERENCE && item.text().startsWith(RoleText.MISSING_PREFIX)) {
                for (String name : item.text().substring(RoleText.MISSING_PREFIX.length()).split(",")) {
                    try {
                        InformationField field = InformationField.valueOf(name.trim());
                        if (role.requiredFields().contains(field)) {
                            missing.add(field);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Only names from the fixed list count; anything else is not a field.
                    }
                }
            }
        }
        return missing;
    }

    private boolean verified(BlackboardSnapshot state) {
        try {
            return state.email().sender() != null && services.senderVerifier().isVerified(state.email());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The role interfaces carry no methods; these subclasses only make {@code instanceof} work. */
    static final class Student extends RoleAgent implements org.supermas.agent.StudentAgent {
        Student(RoleDefinition role, AgentConfig config, String version, RoleServices services) {
            super(role, config, version, services);
        }
    }

    static final class Teacher extends RoleAgent implements org.supermas.agent.TeacherAgent {
        Teacher(RoleDefinition role, AgentConfig config, String version, RoleServices services) {
            super(role, config, version, services);
        }
    }

    static final class Research extends RoleAgent implements org.supermas.agent.ResearchProjectAgent {
        Research(RoleDefinition role, AgentConfig config, String version, RoleServices services) {
            super(role, config, version, services);
        }
    }

    static final class Administration extends RoleAgent implements org.supermas.agent.AdministrationAgent {
        Administration(RoleDefinition role, AgentConfig config, String version, RoleServices services) {
            super(role, config, version, services);
        }
    }

    static final class Generic extends RoleAgent implements org.supermas.agent.GenericAgent {
        Generic(RoleDefinition role, AgentConfig config, String version, RoleServices services) {
            super(role, config, version, services);
        }
    }
}
