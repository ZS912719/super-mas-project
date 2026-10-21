package org.supermas.blackboard;

import org.supermas.model.Domain.*;
import java.util.List;

/** Scoped specialist access. No approval, sending, repository access or peer-agent invocation. */
public interface AgentBlackboard extends BlackboardView {
    String agentId();

    /** Accept only evidence from the current scope and the agent's permitted sources. */
    void submitEvidence(List<Evidence> evidence);

    /** Store a proposal as a new unapproved version; never replace prior versions in place. */
    Draft submitDraft(DraftProposal proposal);

    /** Suggestions enter the pending agenda; the coordinator retains scheduling authority. */
    void proposeTasks(List<Task> tasks);
}
