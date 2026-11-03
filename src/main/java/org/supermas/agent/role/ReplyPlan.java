package org.supermas.agent.role;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;

/**
 * A specialist's structured contribution: what to answer, from which facts and evidence, what is missing, and
 * which rules apply. It is written to the board before any draft, so a reviewer, the team's writer or another
 * component can see why a draft says what it says. Quoted requests and facts are untrusted text from the email;
 * rules are trusted text from the role's configuration.
 */
// ASSUMED-TEAM-DECISION(P11): until the team defines AgentContribution, a plan travels as MODEL_INFERENCE evidence
// whose identifier starts with "reply-plan:" and whose text is this record as JSON. Pending team validation.
// If rejected: map this record onto AgentContribution in ReplyWork, the only place that writes it.
public record ReplyPlan(String planId, String agentId, String role, String promptVersion, String language,
                        List<String> requests, String requestSource, List<Fact> facts, List<Reference> context,
                        List<String> missing, List<String> rules, List<String> notes) {
    public static final String ID_PREFIX = "reply-plan:";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A fact from the email's analysis, such as a course code or a date. */
    public record Fact(String type, String text, String source) { }

    /** Evidence the plan relies on, by identifier, so every claim can be traced on the board. */
    public record Reference(String evidenceId, String sourceId, String kind) { }

    public ReplyPlan {
        requests = List.copyOf(requests);
        facts = List.copyOf(facts);
        context = List.copyOf(context);
        missing = List.copyOf(missing);
        rules = List.copyOf(rules);
        notes = List.copyOf(notes);
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A plan is always serializable", e);
        }
    }

    public static boolean isPlan(Evidence evidence) {
        return evidence.kind() == EvidenceKind.MODEL_INFERENCE && evidence.id().startsWith(ID_PREFIX);
    }

    /** The plan in this evidence, or empty if it is not a plan or cannot be read. */
    public static Optional<ReplyPlan> from(Evidence evidence) {
        if (!isPlan(evidence)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.readValue(evidence.text(), ReplyPlan.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
