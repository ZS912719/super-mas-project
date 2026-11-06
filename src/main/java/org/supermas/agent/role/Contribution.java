package org.supermas.agent.role;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;

/**
 * A specialist's partial solution, at the contribution level of the blackboard (docs/plans/blackboard-control.md):
 * the requests it covers and, for them, its reply plan and short excerpts of the evidence it relied on. Several
 * specialists may contribute to one email, and may cover the same request; the composer resolves overlaps.
 *
 * <p>On the board: a {@code MODEL_INFERENCE} with identifier {@code contribution:<agent>:<n>} and this record as JSON
 * ({@code contribution/v1}). It is promoted only to the composer, never to other specialists, because excerpts come
 * from sources the other roles may not read.
 *
 * @param estimate      the specialist's own fit when it contributed, used by the composer to order overlaps
 * @param covers        request indices it answers
 * @param feedbackSeen  how many feedback items existed when it contributed; more feedback triggers a revision
 * @param excerpts      evidence text it relied on, already cut to its context budget
 */
public record Contribution(String schema, String id, String agentId, String role, double estimate, List<Integer> covers,
                           int feedbackSeen, ReplyPlan plan, List<Excerpt> excerpts) {
    public static final String SCHEMA = "contribution/v1";
    public static final String ID_PREFIX = "contribution:";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Untrusted evidence text, with where it came from. */
    public record Excerpt(String evidenceId, String sourceId, String text) { }

    public Contribution {
        covers = List.copyOf(covers);
        excerpts = List.copyOf(excerpts);
    }

    public Evidence toEvidence(Scope scope, java.time.Instant at) {
        try {
            return new Evidence(id, scope, EvidenceKind.MODEL_INFERENCE,
                    new Provenance(agentId, "Contribution of role " + role + " to requests " + covers, at),
                    JSON.writeValueAsString(this), Set.of(agentId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A contribution is always serializable", e);
        }
    }

    public static boolean isContribution(Evidence evidence) {
        return evidence.kind() == EvidenceKind.MODEL_INFERENCE && evidence.id().startsWith(ID_PREFIX);
    }

    public static Optional<Contribution> from(Evidence evidence) {
        if (!isContribution(evidence)) {
            return Optional.empty();
        }
        try {
            Contribution found = JSON.readValue(evidence.text(), Contribution.class);
            // The author is the evidence's source, which the board checked; a JSON claiming another author is refused.
            return SCHEMA.equals(found.schema()) && found.agentId().equals(evidence.provenance().sourceId())
                    ? Optional.of(found) : Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** The latest contribution of each agent in this evidence, an original and its promoted copy counted once. */
    public static List<Contribution> latest(Collection<Evidence> evidence) {
        Map<String, Contribution> byAgent = new LinkedHashMap<>();
        for (Evidence item : evidence) {
            from(item).ifPresent(found -> {
                Contribution earlier = byAgent.get(found.agentId());
                if (earlier == null || sequence(found.id()) > sequence(earlier.id())) {
                    byAgent.put(found.agentId(), found);
                }
            });
        }
        return List.copyOf(byAgent.values());
    }

    /** The number after the last colon, ignoring any suffix a promoted copy carries. */
    static int sequence(String id) {
        String tail = id.substring(id.lastIndexOf(':') + 1);
        int end = 0;
        while (end < tail.length() && Character.isDigit(tail.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(tail.substring(0, Math.min(end, 9)));
    }
}
