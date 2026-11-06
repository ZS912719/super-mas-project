package org.supermas.agent.role;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import org.supermas.agent.SpecializedAgent;
import org.supermas.agent.collaboration.RequestEntry;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.context.InformationField;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Action;
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
        if (task != null && (task.action() == Action.CONTRIBUTE || task.action() == Action.REVISE)) {
            return contributionEstimate(board.snapshot());
        }
        return fit(board.snapshot());
    }

    /** The role's fit to the whole email: role hint and keywords (docs/plans/reply-agent-assess.md). */
    private AgentAssessment fit(BlackboardSnapshot state) {
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
        if (task != null && (task.action() == Action.CONTRIBUTE || task.action() == Action.REVISE)) {
            return ReplyWork.contribute(this, task, board);
        }
        return ReplyWork.run(this, task, board);
    }

    /**
     * The estimate the blackboard controller asks for before choosing a contribution (Corkill's split of control
     * knowledge): the role's fit, if there are requests it would answer and it has not answered them since the
     * last feedback. It reads only what the board shows this role; it never learns what other specialists did.
     */
    AgentAssessment contributionEstimate(BlackboardSnapshot state) {
        List<RequestEntry> requests = RequestEntry.all(state.evidence());
        if (requests.isEmpty()) {
            return new AgentAssessment(config.id(), 0, true, "No request entries on the board yet");
        }
        if (role.fallback()) {
            return new AgentAssessment(config.id(), 0, true, "Fallback role, asked only when no specialist fits");
        }
        Optional<Contribution> own = Contribution.latest(state.evidence()).stream()
                .filter(found -> found.agentId().equals(config.id())).findFirst();
        if (own.isPresent() && own.get().feedbackSeen() >= state.feedback().size()) {
            return new AgentAssessment(config.id(), 0, true, "Already contributed to requests " + own.get().covers());
        }
        AgentAssessment fit = fit(state);
        List<Integer> covers = covers(state, requests, fit.score());
        if (covers.isEmpty()) {
            return new AgentAssessment(config.id(), 0, fit.sufficientContext(), "No request for this role; " + fit.reason());
        }
        return new AgentAssessment(config.id(), fit.score(), fit.sufficientContext(), (own.isPresent()
                ? "Revision after feedback, " : "") + "would answer requests " + covers + "; " + fit.reason());
    }

    /**
     * Requests this role would answer: those quoting one of its keywords, or every request when the sender's
     * role hint matches the role, or when it fits the email only through the subject. A general entry (no
     * request sentence) is answered by any role that fits at all.
     */
    List<Integer> covers(BlackboardSnapshot state, List<RequestEntry> requests, double fit) {
        if (fit <= 0) {
            return List.of();
        }
        boolean hinted = Optional.ofNullable(state.analysis().metadata().get(MetadataAgent.KEY_ROLE_HINT))
                .filter(role.roleHints()::contains).isPresent();
        List<Integer> byKeyword = requests.stream()
                .filter(entry -> entry.general() || RoleText.anyKeyword(role.keywords(), entry.quote()))
                .map(RequestEntry::index).toList();
        return hinted || byKeyword.isEmpty() ? requests.stream().map(RequestEntry::index).toList() : byKeyword;
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
