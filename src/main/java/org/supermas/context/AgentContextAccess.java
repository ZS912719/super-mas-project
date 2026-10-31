package org.supermas.context;

import java.util.List;
import java.util.Set;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextQuery;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.RetrievalResult;

/**
 * Retrieval tied to one agent's identity, which is fixed when this object is built from trusted
 * configuration. An agent holding it cannot ask as anyone else, because no method takes an agent identifier.
 */
public final class AgentContextAccess {
    private final LuceneContextRetriever retriever;
    private final String agentId;
    private final Set<String> sourceIds;

    public AgentContextAccess(LuceneContextRetriever retriever, AgentConfig agent) {
        this.retriever = retriever;
        this.agentId = agent.id();
        this.sourceIds = Set.copyOf(agent.contextSourceIds());
    }

    public RetrievalResult domain(BoardId board, List<String> queryTerms, Set<String> topicTags, boolean supplemental,
                                  int maxCharacters) {
        return retriever.retrieveDomain(new ContextQuery(board, agentId, sourceIds, queryTerms, supplemental,
                maxCharacters), topicTags);
    }

    public RetrievalResult thread(BoardId board, HistoryQuery query) {
        return retriever.retrieveThread(board, query, agentId);
    }
}
