package org.supermas.context;

import org.supermas.blackboard.BlackboardView;
import org.supermas.model.Domain.*;

/** Prioritize relevant evidence and confirmed sent content; do not treat unsent drafts as sent facts. */
public interface ContextSelector {
    ContextSelection select(BlackboardView board, AgentConfig agent, int maxCharacters);
}
