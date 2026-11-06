package org.supermas.context;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.supermas.agent.SpecializedAgent;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.context.TopicCatalog.DiscoveredTopic;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.InformationNeed;
import org.supermas.model.Domain.RetrievalResult;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/**
 * The context agent for discovered topics. Topics come and go as mail is regrouped, but this agent's identity and
 * rights are fixed by trusted configuration, so discovering a topic can never create an agent or widen what is read.
 * For each email it searches within the few current topics that fit it best, each as its own ranked list, which the
 * merge step then fuses like the lists of declared topics.
 *
 * <p>{@code assess} follows the rules for context agents (docs/plans/context-agent-assess.md): deterministic, no
 * index access, score 0 in another project. The score is 1 when the email's thread already belongs to a topic,
 * otherwise the best keyword score of any topic, as for a declared subject topic.
 */
public final class DiscoveredTopicsAgent implements SpecializedAgent {
    private static final int FULL_MATCH_KEYWORDS = 3;

    private record Fit(DiscoveredTopic topic, double score) { }

    private final AgentConfig config;
    private final String projectId;
    private final TopicCatalog catalog;
    private final AgentContextAccess access;
    private final int maxTopics;
    private final int maxCharacters;

    /**
     * @param config trusted configuration: identity, readable sources, no drafting capability
     * @param maxTopics how many topics one email may search
     * @param maxCharacters evidence budget per topic
     */
    public DiscoveredTopicsAgent(AgentConfig config, String projectId, TopicCatalog catalog,
                                 LuceneContextRetriever retriever, int maxTopics, int maxCharacters) {
        if (config.id().contains(".")) {
            throw new IllegalArgumentException("The agent identifier may not contain '.', which separates topics");
        }
        this.config = config;
        this.projectId = projectId;
        this.catalog = catalog;
        this.access = new AgentContextAccess(retriever, config);
        this.maxTopics = maxTopics;
        this.maxCharacters = maxCharacters;
    }

    @Override
    public AgentConfig config() {
        return config;
    }

    /** The evidence label of one topic: this agent's identifier, a dot, the topic's identifier. */
    public String listName(DiscoveredTopic topic) {
        return config.id() + "." + topic.id();
    }

    @Override
    public AgentAssessment assess(Task task, BlackboardView board) {
        BlackboardSnapshot state = board.snapshot();
        if (!projectId.equals(state.id().scope().projectId())) {
            return new AgentAssessment(config.id(), 0, false, "Another project");
        }
        List<Fit> fits = fits(state);
        double best = fits.isEmpty() ? 0 : fits.get(0).score();
        return new AgentAssessment(config.id(), best, ranAlready(state),
                fits.size() + " of " + catalog.topics(projectId).size() + " discovered topics fit");
    }

    @Override
    public AgentOutcome execute(Task task, AgentBlackboard board) {
        BlackboardSnapshot state = board.snapshot();
        if (!projectId.equals(state.id().scope().projectId())) {
            return new AgentOutcome(TaskStatus.SUCCEEDED, "Another project", List.of(), Optional.empty());
        }
        Optional<InformationNeed> need = task == null ? Optional.empty() : task.need();
        List<Fit> fits = fits(state).stream().limit(need.isPresent() ? 1 : maxTopics).toList();
        try {
            Set<String> existing = new HashSet<>(state.evidence().stream().map(Evidence::id).toList());
            List<Evidence> added = new ArrayList<>();
            for (Fit fit : fits) {
                List<String> terms = new ArrayList<>(need.map(InformationNeed::searchTerms).orElse(List.of()));
                terms.addAll(fit.topic().keywords());
                terms.addAll(state.analysis().keywords());
                terms.add(TopicText.currentText(state));
                RetrievalResult result = access.domain(state.id(), terms, Set.of(fit.topic().tag()), need.isPresent(),
                        maxCharacters);
                for (Evidence item : result.evidence()) {
                    String id = TopicEvidence.id(item.id(), listName(fit.topic()));
                    if (!item.id().equals(state.email().id()) && existing.add(id)) {
                        added.add(new Evidence(id, item.scope(), item.kind(), item.provenance(), item.text(),
                                item.permittedAgentIds()));
                    }
                }
            }
            board.submitEvidence(added);
            return new AgentOutcome(TaskStatus.SUCCEEDED, added.size() + " evidence items from " + fits.size()
                    + " discovered topics", List.of(), Optional.empty());
        } catch (UncheckedIOException e) {
            Failure failure = new Failure(FailureKind.CONTEXT, "The index could not be read", true);
            return new AgentOutcome(TaskStatus.FAILED, failure.message(), List.of(), Optional.of(failure));
        }
    }

    /** Topics that fit this email, best first: the thread's own topic, then by keyword score, then identifier. */
    private List<Fit> fits(BlackboardSnapshot state) {
        String text = TopicText.normalize(state.email().subject() + " " + TopicText.currentText(state));
        String thread = state.id().scope().threadId();
        List<Fit> fits = new ArrayList<>();
        for (DiscoveredTopic topic : catalog.topics(projectId)) {
            double score = topic.threadIds().contains(thread) ? 1
                    : Math.min(1, (double) TopicCatalog.matched(topic, text)
                            / Math.min(FULL_MATCH_KEYWORDS, topic.keywords().size()));
            if (score > 0) {
                fits.add(new Fit(topic, score));
            }
        }
        fits.sort(Comparator.comparingDouble((Fit fit) -> -fit.score()).thenComparing(fit -> fit.topic().id()));
        return fits;
    }

    private boolean ranAlready(BlackboardSnapshot state) {
        String prefix = config.id() + ".";
        boolean hasEvidence = state.evidence().stream().anyMatch(item -> TopicEvidence.isTopicEvidence(item)
                && TopicEvidence.agentId(item.id()).startsWith(prefix));
        boolean addressed = state.tasks().stream().anyMatch(item -> item.status() == TaskStatus.PENDING
                && item.action() == Action.RETRIEVE_CONTEXT && item.agentId().filter(config.id()::equals).isPresent());
        return hasEvidence && !addressed;
    }
}
