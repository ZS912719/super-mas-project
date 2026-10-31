package org.supermas.context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextQuery;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.RetrievalResult;

/**
 * Retrieval over the project indexes. Everything returned is untrusted text, labelled as retrieved
 * evidence with its source, and is never interpreted here.
 */
public final class LuceneContextRetriever implements ContextRetriever {
    private final ProjectIndexes indexes;
    private final Map<String, ContextSource> sources;
    private final Function<String, Optional<AgentConfig>> agents;

    /**
     * @param sources every known source by identifier, from trusted configuration
     * @param agents finds an agent's trusted configuration by identifier
     */
    public LuceneContextRetriever(ProjectIndexes indexes, Map<String, ContextSource> sources,
                                  Function<String, Optional<AgentConfig>> agents) {
        this.indexes = indexes;
        this.sources = Map.copyOf(sources);
        this.agents = agents;
    }

    /**
     * Exact project and thread, and only the requested kinds, so that a draft is not mistaken for a sent
     * message. A thread with no history is a normal result: {@code sufficient} is false, without an error.
     */
    @Override
    public RetrievalResult retrieveThread(BoardId boardId, HistoryQuery query) {
        List<Evidence> evidence = new ArrayList<>();
        for (IndexedDocument document : indexes.forProject(boardId.scope().projectId())
                .thread(boardId.scope(), query.kinds(), query.maxMessages())) {
            String kind = document.kind().orElseThrow().name();
            evidence.add(new Evidence(document.id(), document.scope(), EvidenceKind.RETRIEVED_EVIDENCE,
                    new Provenance(document.sourceId(), "Thread message of kind " + kind, document.recordedAt()),
                    withSubject(document), document.permittedAgentIds()));
        }
        return new RetrievalResult(evidence, !evidence.isEmpty(), evidence.size() + " thread messages found");
    }

    @Override
    public RetrievalResult retrieveDomain(ContextQuery query) {
        throw new UnsupportedOperationException("Added in step 3.3");
    }

    private static String withSubject(IndexedDocument document) {
        return document.subject().isBlank() ? document.text() : document.subject() + "\n\n" + document.text();
    }
}
