package org.supermas.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;

/**
 * One control decision on the control part of the board (BB1's Focus, To-Do-Set and Chosen-Action for one cycle),
 * so that every step can be explained and the controller can tell whether the board changed since a knowledge
 * source last ran. It is written by the coordinator, readable by no agent, and visible in the Markdown projection.
 *
 * @param othersEvidence evidence on the board not written by the chosen agent, when it was chosen
 * @param feedback       feedback items on the board when it was chosen
 */
public record ControlEntry(String schema, int round, String focus, String action, String agentId, double rating,
                           String reason, int othersEvidence, int feedback, List<Rated> todo) {
    public static final String SCHEMA = "control/v1";
    public static final String ID_PREFIX = "control:";
    public static final String SOURCE = "controller";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** One activation of the To-Do-Set: an agent, the action it would take, its estimate and rating. */
    public record Rated(String agentId, String action, String level, double estimate, double rating, String why,
                        Boolean eligible) {
        /** Old persisted decisions have no eligibility record; never infer their threshold. */
        public Rated(String agentId, String action, String level, double estimate, double rating, String why) {
            this(agentId, action, level, estimate, rating, why, null);
        }
    }

    public ControlEntry {
        todo = List.copyOf(todo);
    }

    public Evidence toEvidence(Scope scope, java.time.Instant at) {
        try {
            return new Evidence(ID_PREFIX + round, scope, EvidenceKind.MODEL_INFERENCE,
                    new Provenance(SOURCE, "Control decision of round " + round, at), JSON.writeValueAsString(this), Set.of());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A control entry is always serializable", e);
        }
    }

    public static boolean isControl(Evidence evidence) {
        return evidence.id().startsWith(ID_PREFIX) && SOURCE.equals(evidence.provenance().sourceId());
    }

    public static Optional<ControlEntry> from(Evidence evidence) {
        if (!isControl(evidence)) {
            return Optional.empty();
        }
        try {
            ControlEntry entry = JSON.readValue(evidence.text(), ControlEntry.class);
            return SCHEMA.equals(entry.schema()) ? Optional.of(entry) : Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** How much of the board an agent did not write itself: control entries and its own entries are left out. */
    public static int othersEvidence(BlackboardSnapshot state, String agentId) {
        return (int) state.evidence().stream()
                .filter(item -> !isControl(item) && !item.provenance().sourceId().equals(agentId)).count();
    }

    /** The latest decision that chose this agent, if any. */
    public static Optional<ControlEntry> lastChoice(BlackboardSnapshot state, String agentId) {
        Optional<ControlEntry> found = Optional.empty();
        for (Evidence item : state.evidence()) {
            Optional<ControlEntry> entry = from(item).filter(candidate -> agentId.equals(candidate.agentId()));
            if (entry.isPresent() && (found.isEmpty() || entry.get().round() > found.get().round())) {
                found = entry;
            }
        }
        return found;
    }
}
