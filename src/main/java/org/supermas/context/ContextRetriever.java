package org.supermas.context;

import org.supermas.model.Domain.*;

/** Retrieval returns scoped evidence, not unrestricted blackboard or filesystem access. */
public interface ContextRetriever {
    /** Stage one: exact project and thread matching, with explicit message-kind selection. */
    RetrievalResult retrieveThread(BoardId boardId, HistoryQuery query);

    /** Stage two: only sources permitted to this agent in this project; supports supplemental search. */
    RetrievalResult retrieveDomain(ContextQuery query);
}
