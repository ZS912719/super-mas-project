package org.supermas.context;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.AgentBlackboard;
import org.supermas.blackboard.BlackboardView;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.InformationNeed;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/**
 * Records what seems to be missing as an inference, and suggests one further retrieval. It runs at most once
 * on a board, which is what bounds further retrieval. The suggestion names no agent, because agents do not
 * choose peers, so scheduling stays with the coordinator.
 */
public final class MissingInformationAgent implements SpecializedAgent {
    public static final String CAPABILITY = "missing-information-check";
    /** The inference's text is this prefix, then field names from the fixed list, separated by ", ". */
    public static final String TEXT_PREFIX = "Possibly missing: ";
    private static final String EVIDENCE_ID = "missing-information";
    private static final Map<InformationField, List<String>> SEARCH_TERMS = new EnumMap<>(Map.of(
            InformationField.DATE, List.of("date", "schedule", "calendar"),
            InformationField.DEADLINE, List.of("deadline", "due", "date"),
            InformationField.PERSON, List.of("contact", "name"),
            InformationField.COURSE, List.of("course", "class"),
            InformationField.ID, List.of("reference", "number")));

    private final AgentConfig config;
    private final MissingInformationCheck check;
    private final Set<String> visibleTo;

    /** @param visibleTo the agents that may read the inference, usually those able to reply */
    public MissingInformationAgent(String agentId, MissingInformationCheck check, Set<String> visibleTo) {
        this.config = new AgentConfig(agentId, "context-check", "", Set.of(CAPABILITY), List.of(), Set.of(), false);
        this.check = check;
        this.visibleTo = visibleTo;
    }

    @Override
    public AgentConfig config() {
        return config;
    }

    private static boolean ran(BlackboardSnapshot state) {
        return state.evidence().stream().anyMatch(item -> item.id().equals(EVIDENCE_ID));
    }

    @Override
    public AgentAssessment assess(Task task, BlackboardView board) {
        boolean ran = ran(board.snapshot());
        return new AgentAssessment(config.id(), ran ? 0 : 1, ran, ran ? "Already checked" : "Not checked yet");
    }

    @Override
    public AgentOutcome execute(Task task, AgentBlackboard board) {
        BlackboardSnapshot state = board.snapshot();
        if (ran(state)) {
            return new AgentOutcome(TaskStatus.SUCCEEDED, "Already checked", List.of(), Optional.empty());
        }
        MissingInformation result = check.check(state.email(), state.analysis());
        if (result.fields().isEmpty()) {
            return new AgentOutcome(TaskStatus.SUCCEEDED, "Nothing missing, from " + result.source(), List.of(),
                    Optional.empty());
        }
        Set<String> fields = new TreeSet<>();
        result.fields().forEach(field -> fields.add(field.name()));
        Set<String> readers = new TreeSet<>(visibleTo);
        readers.add(config.id());
        board.submitEvidence(List.of(new Evidence(EVIDENCE_ID, state.id().scope(), EvidenceKind.MODEL_INFERENCE,
                new Provenance(config.id(), "Missing-information check, from " + result.source(), state.email().receivedAt()),
                TEXT_PREFIX + String.join(", ", fields), readers)));
        List<String> terms = new ArrayList<>();
        result.fields().stream().sorted().forEach(field -> terms.addAll(SEARCH_TERMS.get(field)));
        Task further = new Task(state.id().taskId() + "-further-retrieval", state.id(), Action.RETRIEVE_CONTEXT,
                Optional.empty(), TaskStatus.PENDING, "Possibly missing: " + String.join(", ", fields),
                Optional.of(new InformationNeed(fields, terms)));
        return new AgentOutcome(TaskStatus.SUCCEEDED, fields.size() + " fields possibly missing, from " + result.source(),
                List.of(further), Optional.empty());
    }
}
