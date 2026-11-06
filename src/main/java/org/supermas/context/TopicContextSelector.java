package org.supermas.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.ContextSelection;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Provenance;

/**
 * Merges what the topic agents found into budgeted input for one requesting agent: duplicates are folded,
 * rankings are fused with Reciprocal Rank Fusion, and Maximal Marginal Relevance keeps near-copies from
 * filling the budget. Rights are checked for the requester, whatever the view passed in can see.
 */
public final class TopicContextSelector implements ContextSelector {
    /** The constant from the original RRF paper. */
    private static final double RRF_K = 60;
    /** The usual starting balance between relevance and diversity. It has not been tuned here. */
    private static final double MMR_RELEVANCE = 0.7;

    private static final class Merged {
        final Evidence first;
        final Set<String> topics = new TreeSet<>();
        final Set<String> tokens;
        double fused;

        Merged(Evidence first) {
            this.first = first;
            this.tokens = new HashSet<>(Arrays.asList(TopicText.normalize(first.text()).trim().split(" ")));
        }
    }

    @Override
    public ContextSelection select(BlackboardView board, AgentConfig agent, int maxCharacters) {
        List<Merged> remaining = new ArrayList<>(merge(board.snapshot().evidence(), agent));
        double best = remaining.stream().mapToDouble(item -> item.fused).max().orElse(1);
        List<Merged> chosen = new ArrayList<>();
        List<Evidence> selected = new ArrayList<>();
        Set<String> omitted = new TreeSet<>();
        int used = 0;
        while (!remaining.isEmpty()) {
            Merged next = remaining.stream()
                    .max(Comparator.comparingDouble((Merged item) -> marginal(item, best, chosen))
                            .thenComparing(item -> item.first.id(), Comparator.reverseOrder()))
                    .orElseThrow();
            remaining.remove(next);
            int length = next.first.text().length();
            if (used + length > maxCharacters) {
                omitted.add(next.first.provenance().sourceId());
                continue;
            }
            used += length;
            chosen.add(next);
            selected.add(present(next));
        }
        return new ContextSelection(selected, used, new ArrayList<>(omitted));
    }

    /** Each topic contributes by rank, and its entries are stored in rank order. */
    private static List<Merged> merge(List<Evidence> evidence, AgentConfig agent) {
        Map<String, Merged> byDocument = new LinkedHashMap<>();
        Map<String, Integer> nextRank = new LinkedHashMap<>();
        for (Evidence item : evidence) {
            if (item.kind() != EvidenceKind.RETRIEVED_EVIDENCE || !permits(item, agent)) {
                continue;
            }
            boolean fromTopic = TopicEvidence.isTopicEvidence(item);
            String document = fromTopic ? TopicEvidence.documentId(item.id()) : item.id();
            String topic = fromTopic ? TopicEvidence.agentId(item.id()) : "";
            int rank = nextRank.merge(topic, 1, Integer::sum);
            Merged merged = byDocument.computeIfAbsent(document, id -> new Merged(new Evidence(id, item.scope(),
                    item.kind(), item.provenance(), item.text(), item.permittedAgentIds())));
            merged.fused += 1 / (RRF_K + rank);
            if (fromTopic) {
                merged.topics.add(topic);
            }
        }
        return new ArrayList<>(byDocument.values());
    }

    /** P6: being on the board is not enough, so the requester's own rights decide. */
    private static boolean permits(Evidence item, AgentConfig agent) {
        return item.permittedAgentIds().contains(agent.id())
                && agent.contextSourceIds().contains(item.provenance().sourceId());
    }

    private static double marginal(Merged item, double best, List<Merged> chosen) {
        double closest = chosen.stream().mapToDouble(other -> jaccard(item.tokens, other.tokens)).max().orElse(0);
        return MMR_RELEVANCE * item.fused / best - (1 - MMR_RELEVANCE) * closest;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / (a.size() + b.size() - shared);
    }

    /** One entry per document, naming every topic that found it as provenance. */
    private static Evidence present(Merged item) {
        Evidence first = item.first;
        Provenance source = first.provenance();
        String topics = item.topics.isEmpty() ? "" : "; found by topics: "
                + item.topics.stream().collect(Collectors.joining(", "));
        return new Evidence(first.id(), first.scope(), first.kind(),
                new Provenance(source.sourceId(), source.description() + topics, source.recordedAt()),
                first.text(), first.permittedAgentIds());
    }
}
