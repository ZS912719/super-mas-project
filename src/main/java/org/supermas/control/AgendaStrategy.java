package org.supermas.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.supermas.agent.AgentRegistry;
import org.supermas.agent.SpecializedAgent;
import org.supermas.agent.collaboration.RequestEntry;
import org.supermas.agent.collaboration.RequestSplitter;
import org.supermas.agent.role.ComposerAgent;
import org.supermas.agent.role.Contribution;
import org.supermas.blackboard.api.Blackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.NextAction;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/**
 * The blackboard controller (docs/plans/blackboard-control.md), after Hayes-Roth's BB1 control loop: each cycle it
 * asks every knowledge source for an estimate through a read-only view of what that source may see (Corkill's
 * split: KS-specific estimates, generic control here), forms the To-Do-Set, rates it against the current focus,
 * and chooses one activation. It knows no email domain: levels come only from capabilities in trusted
 * configuration.
 *
 * <ul>
 *   <li>Problem: one proposed reply for every request. Solved when the latest draft is still proposed.</li>
 *   <li>Strategy: successive levels, requests, context, contributions, reply. Focus: the first level with an
 *       eligible activation. The To-Do-Set is rated within the focus; when the budget forces the reply level,
 *       lower levels still count at {@link Settings#lowerLevelWeight()}.</li>
 *   <li>Policy: a request or context source runs again only on a relevant event: new feedback, or (context)
 *       a pending further-retrieval task; a contribution needs at least {@link Settings#minContribution()}.
 *       Ties go to registration order.</li>
 *   <li>When no specialist is eligible and nothing was contributed, the fallback role contributes.</li>
 * </ul>
 */
public final class AgendaStrategy implements CoordinationStrategy, ControlRecording {
    public enum Level { REQUESTS, CONTEXT, CONTRIBUTION, REPLY }

    /**
     * @param minContribution  lowest estimate for a specialist to contribute
     * @param lowerLevelWeight weight of levels below a budget-forced focus
     * @param reservedRounds   rounds kept for composing and presenting
     */
    public record Settings(double minContribution, double lowerLevelWeight, int reservedRounds) {
        public static Settings defaults() {
            return new Settings(0.4, 0.3, 2);
        }
    }

    private final AgentRegistry registry;
    private final Settings settings;
    private final Map<BoardId, ControlEntry> decisions = new ConcurrentHashMap<>();

    public AgendaStrategy(AgentRegistry registry, Settings settings) {
        this.registry = registry;
        this.settings = settings;
    }

    @Override
    public Optional<ControlEntry> decision(BoardId board) {
        return Optional.ofNullable(decisions.remove(board));
    }

    static Level level(SpecializedAgent agent) {
        var capabilities = agent.config().capabilities();
        if (capabilities.contains(ComposerAgent.CAPABILITY)) {
            return Level.REPLY;
        }
        if (capabilities.contains(RequestSplitter.CAPABILITY)) {
            return Level.REQUESTS;
        }
        if (capabilities.contains("draft-reply")) {
            return Level.CONTRIBUTION;
        }
        return Level.CONTEXT;
    }

    private static Action action(Level level) {
        return switch (level) {
            case REQUESTS, CONTEXT -> Action.RETRIEVE_CONTEXT;
            case CONTRIBUTION -> Action.CONTRIBUTE;
            case REPLY -> Action.GENERATE;
        };
    }

    @Override
    public NextAction next(BlackboardView board, ExecutionLimits limits) {
        if (!(board instanceof Blackboard control)) {
            throw new IllegalArgumentException("The controller needs the coordinator's board, to give each agent its view");
        }
        BlackboardSnapshot state = control.snapshot();
        Optional<Draft> latest = state.drafts().isEmpty() ? Optional.empty()
                : Optional.of(state.drafts().get(state.drafts().size() - 1));
        if (latest.isPresent() && latest.get().review() == ReviewStatus.PROPOSED) {
            return new NextAction(Action.PRESENT_DRAFT, Optional.empty(), "Problem solved: draft v"
                    + latest.get().version() + " is newer than every contribution and feedback");
        }
        Optional<Task> pendingNeed = state.tasks().stream().filter(task -> task.status() == TaskStatus.PENDING
                && task.action() == Action.RETRIEVE_CONTEXT && task.agentId().isEmpty() && task.need().isPresent())
                .findFirst();
        List<Candidate> candidates = new ArrayList<>();
        for (SpecializedAgent agent : registry.all()) {
            if (agent.config().fallback()) {
                continue;
            }
            Level level = level(agent);
            candidates.add(estimate(control, state, agent, level, pendingNeed));
        }
        boolean requests = !RequestEntry.all(state.evidence()).isEmpty();
        boolean contributed = !Contribution.latest(state.evidence()).isEmpty();
        Level focus = !requests ? Level.REQUESTS
                : candidates.stream().anyMatch(c -> c.level == Level.CONTEXT && c.eligible) ? Level.CONTEXT
                : candidates.stream().anyMatch(c -> c.level == Level.CONTRIBUTION && c.eligible) ? Level.CONTRIBUTION
                : Level.REPLY;
        boolean forced = false;
        if (limits.maxRounds() - state.budgetUsage().rounds() <= settings.reservedRounds() && contributed
                && focus != Level.REPLY) {
            focus = Level.REPLY;
            forced = true;
        }
        Candidate chosen = null;
        for (Candidate candidate : candidates) {
            double weight = candidate.level == focus ? 1
                    : forced && candidate.level.ordinal() < focus.ordinal() ? settings.lowerLevelWeight() : 0;
            candidate.rating = candidate.eligible ? candidate.estimate * weight : 0;
            if (candidate.rating > 0 && (chosen == null || candidate.rating > chosen.rating)) {
                chosen = candidate;
            }
        }
        int round = state.budgetUsage().rounds() + 1;
        if (chosen != null && chosen.level == Level.CONTRIBUTION && state.routing().isEmpty()) {
            return record(state, round, focus, Action.ROUTE, null, 0,
                    "Record the first specialist estimates before contributions (diagnostics)", candidates);
        }
        if (chosen == null) {
            Optional<SpecializedAgent> fallback = registry.fallback();
            if (requests && !contributed && fallback.isPresent()) {
                if (state.routing().isEmpty()) {
                    return record(state, round, Level.CONTRIBUTION, Action.ROUTE, null, 0,
                            "Record the first specialist estimates before the fallback (diagnostics)", candidates);
                }
                return record(state, round, Level.CONTRIBUTION, Action.CONTRIBUTE, fallback.get().config().id(), 1,
                        "No specialist reached " + settings.minContribution() + "; the fallback answers every request",
                        candidates);
            }
            return new NextAction(Action.STOP, Optional.empty(), "No knowledge source can act on this board");
        }
        return record(state, round, focus, action(chosen.level), chosen.agentId, chosen.rating,
                "Focus " + focus + (forced ? " (forced by the round budget)" : "") + ": " + chosen.agentId + " "
                        + format(chosen.rating) + " — " + chosen.why, candidates);
    }

    private Candidate estimate(Blackboard control, BlackboardSnapshot state, SpecializedAgent agent, Level level,
                               Optional<Task> pendingNeed) {
        String id = agent.config().id();
        Candidate candidate = new Candidate(id, level);
        Optional<ControlEntry> last = ControlEntry.lastChoice(state, id);
        // Relevant events for request and context sources: new feedback, or a pending further-retrieval need.
        // Other changes (contributions, other context) do not re-trigger them, so they never loop on each other.
        if ((level == Level.REQUESTS || level == Level.CONTEXT) && last.isPresent()
                && last.get().feedback() == state.feedback().size() && !(level == Level.CONTEXT && pendingNeed.isPresent())) {
            candidate.why = "No relevant event since it last ran";
            return candidate;
        }
        try {
            // A source estimates the task it would actually take (Corkill): a context source that already ran
            // would take the pending further-retrieval task, so it is asked about that task and its need.
            boolean takesNeed = level == Level.CONTEXT && pendingNeed.isPresent() && last.isPresent();
            Task probe = takesNeed ? pendingNeed.get()
                    : new Task("estimate", state.id(), action(level), Optional.of(id), TaskStatus.PENDING, "Estimate");
            AgentAssessment assessment = agent.assess(probe, control.viewFor(agent.config()));
            candidate.estimate = Math.max(0, Math.min(1, assessment.score()));
            candidate.why = assessment.reason();
        } catch (RuntimeException e) {
            candidate.why = "Estimate failed: " + e.getMessage();
        }
        candidate.eligible = candidate.estimate > 0
                && (level != Level.CONTRIBUTION || candidate.estimate >= settings.minContribution());
        return candidate;
    }

    private NextAction record(BlackboardSnapshot state, int round, Level focus, Action action, String agentId,
                              double rating, String reason, List<Candidate> candidates) {
        List<ControlEntry.Rated> todo = candidates.stream()
                .map(c -> new ControlEntry.Rated(c.agentId, action(c.level).name(), c.level.name(), c.estimate, c.rating,
                        c.why)).toList();
        decisions.put(state.id(), new ControlEntry(ControlEntry.SCHEMA, round, focus.name(), action.name(), agentId,
                rating, reason, agentId == null ? 0 : ControlEntry.othersEvidence(state, agentId), state.feedback().size(),
                todo));
        StringBuilder text = new StringBuilder(reason);
        List<String> others = candidates.stream().filter(c -> c.estimate > 0 && !c.agentId.equals(agentId))
                .map(c -> c.agentId + " " + format(c.estimate)).toList();
        if (!others.isEmpty()) {
            text.append(" | other estimates: ").append(String.join(", ", others));
        }
        return new NextAction(action, Optional.ofNullable(agentId), text.toString());
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class Candidate {
        final String agentId;
        final Level level;
        double estimate;
        double rating;
        boolean eligible;
        String why = "";

        Candidate(String agentId, Level level) {
            this.agentId = agentId;
            this.level = level;
        }
    }
}
