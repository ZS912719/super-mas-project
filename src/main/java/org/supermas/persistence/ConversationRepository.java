package org.supermas.persistence;

import org.supermas.model.Domain.*;
import java.util.List;

/** Received, generated, user-edited and confirmed sent messages remain distinct. */
public interface ConversationRepository {
    /** SENT may only describe an externally confirmed send; approval alone is insufficient. */
    void append(ConversationMessage message);
    List<ConversationMessage> find(Scope scope, HistoryQuery query);
}
