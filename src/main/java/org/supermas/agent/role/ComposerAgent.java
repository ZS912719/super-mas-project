package org.supermas.agent.role;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.supermas.agent.SpecializedAgent;
import org.supermas.agent.collaboration.RequestEntry;
import org.supermas.blackboard.api.AgentBlackboard;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.AgentOutcome;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.GenerationResult;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;

/**
 * The knowledge source of the reply level (docs/plans/blackboard-control.md): it merges every specialist's
 * contribution into one reply plan, then asks the generation service for one draft. It reads contributions,
 * requests and its own plans, and nothing else directly; the evidence text it passes on comes as the
 * contributors' excerpts.
 *
 * <p>Overlaps: when several contributions answer a request, the one with the higher estimate comes first for it.
 * A request nobody answered is listed for the reviewer, never answered by the composer itself.
 */
public final class ComposerAgent implements SpecializedAgent {
    public static final String DEFAULT_ID = "composer";
    public static final String CAPABILITY = "compose-reply";

    private final AgentConfig config;
    private final RoleCatalog catalog;
    private final RoleServices services;
    private final int maxInputCharacters;

    /**
     * @param splitterId the request splitter whose entries it reads
     */
    public ComposerAgent(String agentId, RoleCatalog catalog, RoleServices services, String splitterId) {
        Set<String> sources = new LinkedHashSet<>();
        catalog.roles().forEach(role -> sources.add(role.id()));
        sources.add(splitterId);
        sources.add(agentId);
        this.config = new AgentConfig(agentId, "composer", catalog.composerSystemPrompt(List.of()), Set.of(CAPABILITY),
                List.of(), Set.copyOf(sources), false);
        this.catalog = catalog;
        this.services = services;
        this.maxInputCharacters = catalog.roles().stream().mapToInt(RoleDefinition::maxInputCharacters).max().orElse(12_000);
    }

    @Override
    public AgentConfig config() {
        return config;
    }

    /** The contribution identifiers the latest composed plan was built from, or empty if none was composed. */
    private Optional<Set<String>> lastComposition(BlackboardSnapshot state) {
        return state.evidence().stream().filter(item -> item.provenance().sourceId().equals(config.id()))
                .map(ReplyPlan::from).flatMap(Optional::stream)
                .max(Comparator.comparingInt(plan -> Contribution.sequence(plan.planId())))
                .map(plan -> {
                    Set<String> used = new LinkedHashSet<>();
                    plan.context().stream().filter(ref -> ref.evidenceId().startsWith(Contribution.ID_PREFIX))
                            .forEach(ref -> used.add(ref.evidenceId()));
                    return used;
                });
    }

    @Override
    public AgentAssessment assess(Task task, BlackboardView board) {
        BlackboardSnapshot state = board.snapshot();
        List<Contribution> contributions = Contribution.latest(state.evidence());
        if (contributions.isEmpty()) {
            return new AgentAssessment(config.id(), 0, false, "No contribution to compose yet");
        }
        Set<String> current = new LinkedHashSet<>();
        contributions.forEach(found -> current.add(found.id()));
        Optional<Set<String>> used = lastComposition(state);
        Optional<Draft> latest = state.drafts().isEmpty() ? Optional.empty()
                : Optional.of(state.drafts().get(state.drafts().size() - 1));
        if (used.isEmpty() || !used.get().equals(current)) {
            return new AgentAssessment(config.id(), 1, true, "Contributions " + current + " are not composed yet");
        }
        if (latest.isEmpty()) {
            // Composed, but no draft: no generation service is configured. Composing again would change nothing.
            return new AgentAssessment(config.id(), 0, true, "Composed; no generation service wrote a draft");
        }
        if (latest.get().review() == ReviewStatus.REJECTED) {
            return new AgentAssessment(config.id(), 0.5, true, "The draft was rejected; recompose if nobody revises");
        }
        return new AgentAssessment(config.id(), 0, true, "The current contributions are composed");
    }

    @Override
    public AgentOutcome execute(Task task, AgentBlackboard board) {
        BlackboardSnapshot state = board.snapshot();
        List<Contribution> contributions = Contribution.latest(state.evidence());
        if (contributions.isEmpty()) {
            return new AgentOutcome(TaskStatus.SUCCEEDED, "Nothing to compose", List.of(), Optional.empty());
        }
        List<RequestEntry> requests = RequestEntry.all(state.evidence());
        long earlier = state.evidence().stream().filter(item -> item.provenance().sourceId().equals(config.id()))
                .filter(ReplyPlan::isPlan).count();
        ReplyPlan plan = merge(state, requests, contributions, ReplyPlan.ID_PREFIX + config.id() + ":" + (earlier + 1));
        board.submitEvidence(List.of(new Evidence(plan.planId(), state.id().scope(), EvidenceKind.MODEL_INFERENCE,
                new Provenance(config.id(), "Reply plan composed from " + contributions.size() + " contributions",
                        state.email().receivedAt()), plan.toJson(), Set.of(config.id()))));
        if (services.generation().isEmpty()) {
            return new AgentOutcome(TaskStatus.SUCCEEDED, "Plan " + plan.planId() + " composed; no generation service",
                    List.of(), Optional.empty());
        }
        List<String> contributors = contributions.stream().map(Contribution::role).toList();
        String systemPrompt = catalog.composerSystemPrompt(contributors);
        List<Evidence> excerpts = new ArrayList<>();
        for (Contribution contribution : contributions) {
            for (Contribution.Excerpt excerpt : contribution.excerpts()) {
                // Prompt material only, never written to the board: the original evidence is already there.
                excerpts.add(new Evidence(excerpt.evidenceId(), state.id().scope(), EvidenceKind.RETRIEVED_EVIDENCE,
                        new Provenance(excerpt.sourceId(), "Excerpt chosen by " + contribution.agentId(),
                                state.email().receivedAt()), excerpt.text(), Set.of(config.id())));
            }
        }
        Optional<String> feedback = state.feedback().isEmpty() ? Optional.empty()
                : Optional.of(state.feedback().get(state.feedback().size() - 1).text());
        RolePromptBuilder.Built built;
        try {
            built = RolePromptBuilder.build(config.id(), systemPrompt, state.email().subject(), RoleText.currentText(state),
                    plan.toJson(), excerpts, feedback, maxInputCharacters);
        } catch (IllegalStateException e) {
            return failed(new Failure(FailureKind.BUDGET_EXHAUSTED, e.getMessage(), false));
        }
        GenerationResult result;
        try {
            result = services.generation().get().generate(built.request(), services.model());
        } catch (RuntimeException e) {
            return failed(new Failure(FailureKind.AGENT, "The generation service failed: " + e.getMessage(), false));
        }
        if (result.draft().isPresent() == result.failure().isPresent()) {
            return failed(new Failure(FailureKind.INVALID_OUTPUT, "A generation result must hold a draft or a failure", false));
        }
        if (result.failure().isPresent()) {
            return failed(result.failure().get());
        }
        DraftProposal generated = result.draft().get();
        String subject = generated.subject() == null || generated.subject().isBlank()
                ? "Re: " + (state.email().subject() == null ? "" : state.email().subject()) : generated.subject();
        String explanation = "Composed from " + contributions.stream().map(found -> found.role() + " "
                + String.format(java.util.Locale.ROOT, "%.2f", found.estimate()) + " → requests " + found.covers()).toList()
                + "; plan " + plan.planId() + ", prompt " + version(systemPrompt) + ", " + built.contextIncluded()
                + " excerpts" + (built.contextOmitted() > 0 ? ", " + built.contextOmitted() + " left out for budget" : "")
                + (feedback.isPresent() ? ", after feedback" : "");
        board.submitDraft(new DraftProposal(subject, generated.body(), config.id(), explanation));
        return new AgentOutcome(TaskStatus.SUCCEEDED, "Draft composed from " + contributors, List.of(), Optional.empty());
    }

    /** One plan for the whole email: every request in order, with who answers it, and everything still missing. */
    ReplyPlan merge(BlackboardSnapshot state, List<RequestEntry> requests, List<Contribution> contributions, String planId) {
        List<Contribution> ranked = contributions.stream()
                .sorted(Comparator.comparingDouble(Contribution::estimate).reversed()).toList();
        Map<String, ReplyPlan.Fact> facts = new LinkedHashMap<>();
        Set<String> missing = new LinkedHashSet<>();
        List<String> rules = new ArrayList<>();
        List<ReplyPlan.Reference> context = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (Contribution contribution : ranked) {
            context.add(new ReplyPlan.Reference(contribution.id(), contribution.agentId(), EvidenceKind.MODEL_INFERENCE.name()));
            contribution.plan().facts().forEach(fact -> facts.putIfAbsent(fact.type() + "\u0000" + fact.text(), fact));
            missing.addAll(contribution.plan().missing());
            contribution.plan().rules().forEach(rule -> rules.add("[" + contribution.role() + "] " + rule));
            context.addAll(contribution.plan().context());
            contribution.plan().notes().forEach(note -> notes.add("[" + contribution.role() + "] " + note));
        }
        List<String> quotes = new ArrayList<>();
        for (RequestEntry request : requests) {
            List<String> by = ranked.stream().filter(found -> found.covers().contains(request.index()))
                    .map(Contribution::role).toList();
            if (!request.general()) {
                quotes.add(request.quote());
            }
            notes.add(0 < by.size() ? "Request " + request.index() + " answered by " + by
                    : "Request " + request.index() + " is answered by no specialist; the reviewer must answer it");
        }
        String language = Optional.ofNullable(state.analysis().metadata().get(MetadataAgent.KEY_LANGUAGE))
                .orElse(ranked.get(0).plan().language());
        return new ReplyPlan(planId, config.id(), "composer", version(catalog.composerSystemPrompt(
                ranked.stream().map(Contribution::role).toList())), language, quotes, "board",
                List.copyOf(facts.values()), context, List.copyOf(missing), rules, notes);
    }

    private static String version(String prompt) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    private static AgentOutcome failed(Failure failure) {
        return new AgentOutcome(TaskStatus.FAILED, failure.message(), List.of(), Optional.of(failure));
    }
}
