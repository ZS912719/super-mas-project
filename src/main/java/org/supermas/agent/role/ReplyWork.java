package org.supermas.agent.role;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.context.AgentContextAccess;
import org.supermas.context.InformationField;
import org.supermas.context.TopicEvidence;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.ContextSelection;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.Feedback;
import org.supermas.model.Domain.GenerationResult;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.RetrievalResult;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/**
 * What a role agent does when asked to answer: gather context it may read, write a reply plan to the board, and,
 * when a generation service is configured, turn the plan into a draft. Every step uses the agent's own restricted
 * board access, so rights are checked by the board and the retriever, not here.
 */
final class ReplyWork {
    /** The thread's received and sent messages; drafts are history of proposals, not of what was said. */
    private static final HistoryQuery THREAD = new HistoryQuery(Set.of(MessageKind.RECEIVED, MessageKind.SENT), 20);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ENTITY_FIELDS = Set.of("DATE", "DEADLINE", "PERSON", "COURSE", "ID");

    private ReplyWork() { }

    static AgentOutcome run(RoleAgent agent, Task task, AgentBlackboard board) {
        List<String> notes = new ArrayList<>();
        searchOwnSources(agent, board, notes);
        BlackboardSnapshot state = board.snapshot();
        ContextSelection selection = agent.services.selector().select(board, agent.config, agent.role.maxContextCharacters());
        if (!selection.omittedSourceIds().isEmpty()) {
            notes.add("Context over budget was left out from sources " + selection.omittedSourceIds());
        }
        ReplyPlan plan = plan(agent, state, selection, notes);
        board.submitEvidence(List.of(new Evidence(plan.planId(), state.id().scope(), EvidenceKind.MODEL_INFERENCE,
                new Provenance(agent.config.id(), "Reply plan of role " + agent.role.id() + ", prompt "
                        + agent.promptVersion, state.email().receivedAt()), plan.toJson(), Set.of(agent.config.id()))));
        if (agent.services.generation().isEmpty()) {
            return new AgentOutcome(TaskStatus.SUCCEEDED, "Reply plan " + plan.planId()
                    + " written; no generation service is configured", List.of(), Optional.empty());
        }
        RolePromptBuilder.Built built;
        try {
            built = RolePromptBuilder.build(agent.config.id(), agent.config.systemPrompt(), state.email().subject(),
                    RoleText.currentText(state), plan.toJson(), selection.evidence(), latestFeedback(state),
                    agent.role.maxInputCharacters());
        } catch (IllegalStateException e) {
            return failed(new Failure(FailureKind.BUDGET_EXHAUSTED, e.getMessage(), false));
        }
        GenerationResult result;
        try {
            result = agent.services.generation().get().generate(built.request(), agent.services.model());
        } catch (RuntimeException e) {
            return failed(new Failure(FailureKind.AGENT, "The generation service failed: " + e.getMessage(), false));
        }
        if (result.draft().isPresent() == result.failure().isPresent()) {
            return failed(new Failure(FailureKind.INVALID_OUTPUT, "A generation result must hold a draft or a failure", false));
        }
        if (result.failure().isPresent()) {
            return failed(result.failure().get());
        }
        DraftProposal generated = result.draft().get();
        String subject = generated.subject() == null || generated.subject().isBlank()
                ? "Re: " + (state.email().subject() == null ? "" : state.email().subject()) : generated.subject();
        String explanation = "Plan " + plan.planId() + ", prompt " + agent.promptVersion + ", " + built.contextIncluded()
                + " context items" + (built.contextOmitted() > 0 ? ", " + built.contextOmitted() + " left out for budget" : "")
                + (built.truncated() ? ", email or feedback cut for budget" : "");
        board.submitDraft(new DraftProposal(subject, generated.body(), agent.config.id(), explanation));
        return new AgentOutcome(TaskStatus.SUCCEEDED, "Draft written from plan " + plan.planId(), List.of(), Optional.empty());
    }

    /** The agent's own search: the thread's history, then a search of its sources, as C1 plus thread history. */
    private static void searchOwnSources(RoleAgent agent, AgentBlackboard board, List<String> notes) {
        if (agent.services.retriever().isEmpty()) {
            return;
        }
        BlackboardSnapshot state = board.snapshot();
        AgentContextAccess access = new AgentContextAccess(agent.services.retriever().get(), agent.config);
        Set<String> existing = new HashSet<>(state.evidence().stream().map(Evidence::id).toList());
        List<Evidence> added = new ArrayList<>();
        try {
            List<String> terms = new ArrayList<>(state.analysis().keywords());
            terms.add(RoleText.currentText(state));
            addList(added, existing, access.thread(state.id(), THREAD), agent.config.id() + ".thread", state);
            addList(added, existing, access.domain(state.id(), terms, Set.of(), false, agent.role.maxContextCharacters()),
                    agent.config.id() + ".search", state);
            board.submitEvidence(added);
        } catch (UncheckedIOException e) {
            notes.add("The agent's own search failed; only context already on the board was used");
        }
    }

    private static void addList(List<Evidence> added, Set<String> existing, RetrievalResult result, String list,
                                BlackboardSnapshot state) {
        for (Evidence item : result.evidence()) {
            String id = TopicEvidence.id(item.id(), list);
            if (!item.id().equals(state.email().id()) && existing.add(id)) {
                added.add(new Evidence(id, item.scope(), item.kind(), item.provenance(), item.text(), item.permittedAgentIds()));
            }
        }
    }

    private static ReplyPlan plan(RoleAgent agent, BlackboardSnapshot state, ContextSelection selection,
                                  List<String> notes) {
        String language = Optional.ofNullable(state.analysis().metadata().get(MetadataAgent.KEY_LANGUAGE)).orElse("en");
        RequestExtractor.Requests requests = agent.services.requests().extract(RoleText.currentText(state), language);
        List<ReplyPlan.Fact> facts = new ArrayList<>();
        Set<String> present = new TreeSet<>();
        for (Map<String, Object> entity : entities(state)) {
            String type = String.valueOf(entity.get("type"));
            facts.add(new ReplyPlan.Fact(type, String.valueOf(entity.get("text")), "email"));
            present.add(type);
        }
        Set<String> missing = new TreeSet<>();
        for (InformationField field : agent.role.requiredFields()) {
            if (ENTITY_FIELDS.contains(field.name()) && !present.contains(field.name())) {
                missing.add(field.name());
            }
        }
        agent.missingRequired(state).forEach(field -> missing.add(field.name()));
        List<ReplyPlan.Reference> context = selection.evidence().stream()
                .map(item -> new ReplyPlan.Reference(item.id(), item.provenance().sourceId(), item.kind().name()))
                .toList();
        long earlier = state.evidence().stream()
                .filter(item -> item.id().startsWith(ReplyPlan.ID_PREFIX + agent.config.id() + ":")).count();
        return new ReplyPlan(ReplyPlan.ID_PREFIX + agent.config.id() + ":" + (earlier + 1), agent.config.id(),
                agent.role.id(), agent.promptVersion, language, requests.requests(), requests.source(), facts, context,
                List.copyOf(missing), agent.role.rules(), notes);
    }

    private static List<Map<String, Object>> entities(BlackboardSnapshot state) {
        String json = state.analysis().metadata().get(MetadataAgent.KEY_ENTITIES);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Optional<String> latestFeedback(BlackboardSnapshot state) {
        List<Feedback> feedback = state.feedback();
        return feedback.isEmpty() ? Optional.empty() : Optional.of(feedback.get(feedback.size() - 1).text());
    }

    private static AgentOutcome failed(Failure failure) {
        return new AgentOutcome(TaskStatus.FAILED, failure.message(), List.of(), Optional.of(failure));
    }
}
