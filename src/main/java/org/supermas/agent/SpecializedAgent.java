package org.supermas.agent;

import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.model.Domain.*;

/** Agents declare suitability; they do not select peers or grant their own permissions. */
public interface SpecializedAgent {
    AgentConfig config();
    AgentAssessment assess(Task task, BlackboardView board);

    /** Submit contributions through the restricted blackboard; return an execution summary. */
    AgentOutcome execute(Task task, AgentBlackboard board);
}
