package org.supermas.coordinator;

import org.supermas.blackboard.api.BlackboardView;
import org.supermas.model.Domain.*;
import java.util.List;

/** Must distinguish clear, ambiguous and absent matches, retaining candidates and reasons. */
public interface RoutingStrategy {
    RoutingDecision select(BlackboardView board, List<AgentAssessment> candidates);
}
