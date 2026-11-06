package org.supermas.prototype;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.coordinator.RoutingStrategy;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.Match;
import org.supermas.model.Domain.RoutingDecision;

// PROTOTYPE-STAND-IN: owned by the team's routing work, not by the Metadata/Context agents.
// Required by these agents: clear, ambiguous and absent matches, with candidates and reasons retained.
// Replace with the real implementation.
/** Clear when the best score reaches the minimum and leads the next score by the margin. */
public final class ScoreRoutingStrategy implements RoutingStrategy {
    private final double minimumScore;
    private final double margin;

    public ScoreRoutingStrategy(double minimumScore, double margin) {
        this.minimumScore = minimumScore;
        this.margin = margin;
    }

    @Override
    public RoutingDecision select(BlackboardView board, List<AgentAssessment> candidates) {
        List<AgentAssessment> ranked = candidates.stream()
                .sorted(Comparator.comparingDouble(AgentAssessment::score).reversed()).toList();
        if (ranked.isEmpty() || ranked.get(0).score() < minimumScore) {
            return new RoutingDecision(Match.NONE, Optional.empty(), ranked, "No candidate reached the minimum score");
        }
        AgentAssessment best = ranked.get(0);
        if (ranked.size() > 1 && best.score() - ranked.get(1).score() < margin) {
            return new RoutingDecision(Match.AMBIGUOUS, Optional.empty(), ranked,
                    best.agentId() + " and " + ranked.get(1).agentId() + " scored too closely");
        }
        return new RoutingDecision(Match.CLEAR, Optional.of(best.agentId()), ranked, best.agentId() + " leads");
    }
}
