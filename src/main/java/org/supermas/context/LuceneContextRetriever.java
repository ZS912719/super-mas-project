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
    private static final int INITIAL_RESULTS = 10;
    private static final int SUPPLEMENTAL_RESULTS = 30;

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

    /**
     * Thread history for one agent. The thread itself is exact, and each message must also pass the same
     * source rights as domain search, so a thread does not become a way around them.
     */
    public RetrievalResult retrieveThread(BoardId boardId, HistoryQuery query, String agentId) {
        String project = boardId.scope().projectId();
        List<Evidence> evidence = new ArrayList<>();
        for (Evidence item : retrieveThread(boardId, query).evidence()) {
            String source = item.provenance().sourceId();
            if (isAuthorized(source, project, agentId) && item.permittedAgentIds().contains(agentId)) {
                Set<String> permitted = new HashSet<>(item.permittedAgentIds());
                permitted.retainAll(sources.get(source).permittedAgentIds());
                evidence.add(new Evidence(item.id(), item.scope(), item.kind(), item.provenance(), item.text(), permitted));
            }
        }
        return new RetrievalResult(evidence, !evidence.isEmpty(), evidence.size() + " permitted thread messages found");
    }

    /** Authorized domain search with no topic restriction. */
    @Override
    public RetrievalResult retrieveDomain(ContextQuery query) {
        return retrieveDomain(query, Set.of());
    }

    /**
     * Rights are checked before any index is read. A source is usable only if it belongs to the board's
     * project, permits the requester, and is in the requester's configuration. {@code query.agentId()} is
     * the requester, not whoever makes the call, so a broad agent cannot fetch for a narrower one beyond
     * the narrower one's rights.
     *
     * <p>Results carry the board's scope, as the board requires. The originating thread is in the provenance.
     * Results that do not fit the budget are left out whole and counted, and are never cut.
     *
     * @param topicTags when not empty, only documents with one of these tags
     */
    public RetrievalResult retrieveDomain(ContextQuery query, Set<String> topicTags) {
        String project = query.boardId().scope().projectId();
        Set<String> allowed = new HashSet<>();
        List<String> denied = new ArrayList<>();
        for (String sourceId : query.sourceIds()) {
            (isAuthorized(sourceId, project, query.agentId()) ? allowed : denied).add(sourceId);
        }
        java.util.Collections.sort(denied);
        int limit = query.supplemental() ? SUPPLEMENTAL_RESULTS : INITIAL_RESULTS;
        List<Evidence> evidence = new ArrayList<>();
        int used = 0;
        int omitted = 0;
        for (ProjectIndex.Hit hit : indexes.forProject(project)
                .search(String.join(" ", query.keywords()), allowed, query.agentId(), topicTags, limit)) {
            IndexedDocument document = hit.document();
            String text = withSubject(document);
            if (used + text.length() > query.maxCharacters()) {
                omitted++;
                continue;
            }
            used += text.length();
            Set<String> permitted = new HashSet<>(document.permittedAgentIds());
            permitted.retainAll(sources.get(document.sourceId()).permittedAgentIds());
            evidence.add(new Evidence(document.id(), query.boardId().scope(), EvidenceKind.RETRIEVED_EVIDENCE,
                    new Provenance(document.sourceId(), "From thread " + document.scope().threadId(),
                            document.recordedAt()), text, permitted));
        }
        return new RetrievalResult(evidence, !evidence.isEmpty(), evidence.size() + " results, " + omitted
                + " over budget, denied sources: " + denied);
    }

    private boolean isAuthorized(String sourceId, String project, String agentId) {
        ContextSource source = sources.get(sourceId);
        return source != null
                && project.equals(source.projectId())
                && source.permittedAgentIds().contains(agentId)
                && agents.apply(agentId).map(agent -> agent.contextSourceIds().contains(sourceId)).orElse(false);
    }

    private static String withSubject(IndexedDocument document) {
        return document.subject().isBlank() ? document.text() : document.subject() + "\n\n" + document.text();
    }
}
