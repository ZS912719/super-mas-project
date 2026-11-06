package org.supermas.prototype;

import java.util.Optional;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.NextAction;

// PROTOTYPE-STAND-IN: owned by the team's scheduling work, not by the Metadata/Context agents.
// Required by these agents: the next action chosen from current board state, one action at a time.
// Replace with the real implementation.
/** Route, then generate, then present. The coordinator resolves an unnamed generator from routing. */
public final class DraftFirstCoordinationStrategy implements CoordinationStrategy {
    @Override
    public NextAction next(BlackboardView board, ExecutionLimits limits) {
        BlackboardSnapshot state = board.snapshot();
        if (!state.drafts().isEmpty()) {
            return new NextAction(Action.PRESENT_DRAFT, Optional.empty(), "A draft is ready");
        }
        if (state.routing().isEmpty()) {
            return new NextAction(Action.ROUTE, Optional.empty(), "No routing decision yet");
        }
        return new NextAction(Action.GENERATE, state.routing().get().selectedAgentId(), "Routed, without a draft");
    }
}
