package org.supermas.prototype;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.supermas.context.TopicEvidence;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;

// PROTOTYPE-STAND-IN: owned by the team's coordinator work, not by the Metadata/Context agents.
// Required by these agents: what a context agent finds reaches the reply agents allowed to read it. The durable
// blackboard (codex/blackboard-storage) keeps every agent contribution private to its author "until coordinator
// promotion", and leaves the promotion rule to the coordinator. This class is that rule for the prototype.
// Replace with the real implementation.
/**
 * Decides which agents may read a copy of a context agent's private contribution. Only trusted configuration
 * and the index decide; nothing the agent wrote widens access.
 *
 * <ul>
 *   <li>An agent is a reader only if its configuration lists the evidence's source, because the board shows an
 *       agent only evidence from its own sources.</li>
 *   <li>Retrieved evidence also needs the document's own readers, as retrieval computes them. A document the
 *       index does not know is not promoted, so a failed lookup never widens access.</li>
 *   <li>An inference's source is its author, so the readers are the agents configured to read that author.</li>
 * </ul>
 *
 * <p>The copy keeps kind, provenance and text, and gets the identifier of the original plus {@link #SUFFIX},
 * because the board refuses a second item with the same identifier. The suffix comes after the topic marker,
 * so the document identifier used by the context merge is unchanged. The author is left out of the copy's
 * readers, since it already reads its original.
 */
public final class EvidencePromotion {
    public static final String SUFFIX = "@shared";

    /** Who may read an indexed document, or empty when the document is unknown. */
    @FunctionalInterface
    public interface DocumentReaders {
        Optional<Set<String>> readers(String projectId, String documentId, String sourceId);
    }

    private final DocumentReaders documents;

    /** @param documents usually {@code LuceneContextRetriever::readers} */
    public EvidencePromotion(DocumentReaders documents) {
        this.documents = documents;
    }

    /** No index: inferences are promoted, retrieved evidence is not. */
    public static EvidencePromotion inferencesOnly() {
        return new EvidencePromotion((project, document, source) -> Optional.empty());
    }

    /**
     * Copies of the author's new private contributions for the agents allowed to read them.
     *
     * @param added evidence that appeared on the board during the author's task
     * @param agents the trusted configurations of every registered agent
     */
    public List<Evidence> promote(AgentConfig author, List<Evidence> added, Collection<AgentConfig> agents) {
        return added.stream()
                .filter(item -> item.permittedAgentIds().equals(Set.of(author.id())))
                .filter(item -> !item.id().endsWith(SUFFIX))
                .map(item -> copy(item, readers(author, item, agents)))
                .filter(item -> !item.permittedAgentIds().isEmpty())
                .toList();
    }

    private Set<String> readers(AgentConfig author, Evidence item, Collection<AgentConfig> agents) {
        String source = item.provenance().sourceId();
        Set<String> readers = new TreeSet<>();
        for (AgentConfig agent : agents) {
            if (!agent.id().equals(author.id()) && agent.contextSourceIds().contains(source)) {
                readers.add(agent.id());
            }
        }
        if (item.kind() == EvidenceKind.MODEL_INFERENCE) {
            // An inference may only come from its author: the board checks the source is one the author may use.
            return source.equals(author.id()) ? readers : Set.of();
        }
        if (item.kind() != EvidenceKind.RETRIEVED_EVIDENCE) {
            return Set.of();
        }
        String document = TopicEvidence.isTopicEvidence(item) ? TopicEvidence.documentId(item.id()) : item.id();
        Set<String> allowed = new HashSet<>(documents.readers(item.scope().projectId(), document, source)
                .orElse(Set.of()));
        readers.retainAll(allowed);
        return readers;
    }

    private static Evidence copy(Evidence item, Set<String> readers) {
        return new Evidence(item.id() + SUFFIX, item.scope(), item.kind(), item.provenance(), item.text(),
                Set.copyOf(readers));
    }
}
