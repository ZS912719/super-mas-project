package org.supermas.context;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.AgentBlackboard;
import org.supermas.blackboard.BlackboardView;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.InformationNeed;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.RetrievalResult;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/**
 * A filtered view over the project's shared index, with no LLM. Adding a topic is a matter of
 * configuration. The meaning of {@code assess} is defined in docs/plans/context-agent-assess.md.
 */
public final class TopicContextAgent implements SpecializedAgent {
    public static final String CAPABILITY = "context-retrieval";
    private static final int FULL_MATCH_KEYWORDS = 3;
    private static final int MAX_THREAD_MESSAGES = 50;
    /** Drafts are left out, so an unsent draft can never be read as something that was said. */
    private static final HistoryQuery THREAD_HISTORY = new HistoryQuery(
            Set.of(MessageKind.RECEIVED, MessageKind.SENT), MAX_THREAD_MESSAGES);

    private final TopicDefinition topic;
    private final AgentConfig config;
    private final AgentContextAccess access;

    public TopicContextAgent(TopicDefinition topic, LuceneContextRetriever retriever) {
        this.topic = topic;
        // A fixed template: email text can never reach an agent's configuration.
        this.config = new AgentConfig(topic.agentId(), "context-" + topic.type().name().toLowerCase(java.util.Locale.ROOT),
                "", Set.of(CAPABILITY), topic.keywords(), topic.sourceIds(), false);
        this.access = new AgentContextAccess(retriever, config);
    }

    public TopicDefinition topic() {
        return topic;
    }

    @Override
    public AgentConfig config() {
        return config;
    }

    @Override
    public AgentAssessment assess(Task task, BlackboardView board) {
        BlackboardSnapshot state = board.snapshot();
        if (!topic.projectId().equals(state.id().scope().projectId())) {
            return new AgentAssessment(config.id(), 0, false, "Topic belongs to another project");
        }
        if (topic.type() == TopicType.DISCUSSION) {
            return new AgentAssessment(config.id(), 1, ranAlready(state), "Every email has a thread");
        }
        long matched = matchedKeywords(state);
        double score = topic.keywords().isEmpty() ? 0
                : Math.min(1, (double) matched / Math.min(FULL_MATCH_KEYWORDS, topic.keywords().size()));
        return new AgentAssessment(config.id(), score, ranAlready(state), matched + " topic keywords matched");
    }

    private long matchedKeywords(BlackboardSnapshot state) {
        String text = TopicText.normalize(state.email().subject() + " " + TopicText.currentText(state));
        return topic.keywords().stream().distinct().filter(keyword -> TopicText.contains(text, keyword)).count();
    }

    private boolean ranAlready(BlackboardSnapshot state) {
        boolean hasEvidence = state.evidence().stream().anyMatch(item -> TopicEvidence.isTopicEvidence(item)
                && config.id().equals(TopicEvidence.agentId(item.id())));
        boolean addressed = state.tasks().stream().anyMatch(item -> item.status() == TaskStatus.PENDING
                && item.action() == Action.RETRIEVE_CONTEXT && item.agentId().filter(config.id()::equals).isPresent());
        return hasEvidence && !addressed;
    }

    @Override
    public AgentOutcome execute(Task task, AgentBlackboard board) {
        BlackboardSnapshot state = board.snapshot();
        if (!topic.projectId().equals(state.id().scope().projectId())) {
            return new AgentOutcome(TaskStatus.SUCCEEDED, "Topic belongs to another project", List.of(), Optional.empty());
        }
        try {
            RetrievalResult result = needOf(task).map(need -> furtherRetrieval(state, need)).orElseGet(() -> retrieve(state));
            int added = submit(result.evidence(), state, board);
            return new AgentOutcome(TaskStatus.SUCCEEDED, added + " evidence items added", List.of(), Optional.empty());
        } catch (UncheckedIOException e) {
            Failure failure = new Failure(FailureKind.CONTEXT, "The index could not be read", true);
            return new AgentOutcome(TaskStatus.FAILED, failure.message(), List.of(), Optional.of(failure));
        }
    }

    private static Optional<InformationNeed> needOf(Task task) {
        return task == null ? Optional.empty() : task.need();
    }

    /** A wider search with the need's fixed terms added. Subject types only: a thread is already complete. */
    private RetrievalResult furtherRetrieval(BlackboardSnapshot state, InformationNeed need) {
        if (topic.type() == TopicType.DISCUSSION) {
            return new RetrievalResult(List.of(), false, "A thread has nothing further to find");
        }
        List<String> terms = new ArrayList<>(need.searchTerms());
        terms.addAll(queryTerms(state));
        return access.domain(state.id(), terms, Set.of(topic.tag()), true, topic.maxCharacters());
    }

    private RetrievalResult retrieve(BlackboardSnapshot state) {
        if (topic.type() == TopicType.DISCUSSION) {
            return newestWithinBudget(access.thread(state.id(), THREAD_HISTORY));
        }
        return access.domain(state.id(), queryTerms(state), Set.of(topic.tag()), false, topic.maxCharacters());
    }

    /** Newest first, as the rank. Messages that do not fit are left out whole, never cut. */
    private RetrievalResult newestWithinBudget(RetrievalResult thread) {
        List<Evidence> newestFirst = new ArrayList<>(thread.evidence());
        java.util.Collections.reverse(newestFirst);
        List<Evidence> kept = new ArrayList<>();
        int used = 0;
        for (Evidence item : newestFirst) {
            if (used + item.text().length() <= topic.maxCharacters()) {
                used += item.text().length();
                kept.add(item);
            }
        }
        return new RetrievalResult(kept, !kept.isEmpty(), thread.explanation());
    }

    /** Trusted topic words first, because a very long email reaches the query term limit. */
    private List<String> queryTerms(BlackboardSnapshot state) {
        List<String> terms = new ArrayList<>(topic.keywords());
        terms.addAll(state.analysis().keywords());
        terms.add(TopicText.currentText(state));
        return terms;
    }

    /** Stored in rank order. A document this topic already found is not added again. */
    private int submit(List<Evidence> found, BlackboardSnapshot state, AgentBlackboard board) {
        Set<String> existing = new HashSet<>(state.evidence().stream().map(Evidence::id).toList());
        List<Evidence> added = new ArrayList<>();
        for (Evidence item : found) {
            String id = TopicEvidence.id(item.id(), config.id());
            // Once indexed, the email being answered would otherwise be returned as its own context.
            if (!item.id().equals(state.email().id()) && existing.add(id)) {
                added.add(new Evidence(id, item.scope(), item.kind(), item.provenance(), item.text(),
                        item.permittedAgentIds()));
            }
        }
        board.submitEvidence(added);
        return added.size();
    }
}
