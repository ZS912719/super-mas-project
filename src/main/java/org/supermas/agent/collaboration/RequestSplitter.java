package org.supermas.agent.collaboration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.supermas.agent.SpecializedAgent;
import org.supermas.agent.role.RequestExtractor;
import org.supermas.agent.role.RuleRequestExtractor;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/**
 * The knowledge source of the request level: it splits the email into the requests it contains, so that several
 * specialists can each answer the part they know. It runs once per email, before anything is contributed.
 *
 * <p>Its entries are its own inferences, so its identifier is its only source; the coordinator promotes them to
 * the agents whose configuration lists that identifier (the specialists and the composer).
 */
public final class RequestSplitter implements SpecializedAgent {
    public static final String DEFAULT_ID = "request-splitter";
    public static final String CAPABILITY = "request-splitting";

    private final AgentConfig config;
    private final RequestExtractor extractor;

    public RequestSplitter(String agentId, RequestExtractor extractor) {
        this.config = new AgentConfig(agentId, "request-splitter", "", Set.of(CAPABILITY), List.of(), Set.of(agentId), false);
        this.extractor = extractor;
    }

    public static RequestSplitter withRules() {
        return new RequestSplitter(DEFAULT_ID, new RuleRequestExtractor());
    }

    @Override
    public AgentConfig config() {
        return config;
    }

    private boolean done(BlackboardSnapshot state) {
        return state.evidence().stream().anyMatch(item -> item.provenance().sourceId().equals(config.id())
                && RequestEntry.from(item).isPresent());
    }

    @Override
    public AgentAssessment assess(Task task, BlackboardView board) {
        boolean done = done(board.snapshot());
        return new AgentAssessment(config.id(), done ? 0 : 1, true, done ? "Requests already on the board"
                : "No request entries yet");
    }

    @Override
    public AgentOutcome execute(Task task, AgentBlackboard board) {
        BlackboardSnapshot state = board.snapshot();
        if (done(state)) {
            return new AgentOutcome(TaskStatus.SUCCEEDED, "Requests already on the board", List.of(), Optional.empty());
        }
        String text = Optional.ofNullable(state.analysis().metadata().get(MetadataAgent.KEY_CURRENT_TEXT))
                .orElse(state.email().body() == null ? "" : state.email().body());
        String language = Optional.ofNullable(state.analysis().metadata().get(MetadataAgent.KEY_LANGUAGE)).orElse("en");
        RequestExtractor.Requests found = extractor.extract(text, language);
        List<Evidence> entries = new ArrayList<>();
        if (found.requests().isEmpty()) {
            entries.add(RequestEntry.generalEntry().toEvidence(state.id().scope(), config.id(), state.email().receivedAt()));
        }
        for (int i = 0; i < found.requests().size(); i++) {
            entries.add(RequestEntry.of(i + 1, found.requests().get(i))
                    .toEvidence(state.id().scope(), config.id(), state.email().receivedAt()));
        }
        board.submitEvidence(entries);
        return new AgentOutcome(TaskStatus.SUCCEEDED, found.requests().isEmpty() ? "No sentence reads as a request; "
                + "one general entry" : found.requests().size() + " requests, from " + found.source(), List.of(),
                Optional.empty());
    }
}
