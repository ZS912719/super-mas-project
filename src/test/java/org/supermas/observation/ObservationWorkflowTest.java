package org.supermas.observation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.agent.SpecializedAgent;
import org.supermas.agent.collaboration.RequestSplitter;
import org.supermas.agent.role.*;
import org.supermas.blackboard.api.*;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.blackboard.persistence.codec.JsonCodec;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;
import org.supermas.control.AgendaStrategy;
import org.supermas.model.Domain.*;
import org.supermas.prototype.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.supermas.blackboard.persistence.BlackboardKeys.boardKey;

/** Real leased coordinator and SQLite, controlled agents and fixed model output; no external services. */
class ObservationWorkflowTest {
    @TempDir Path root;
    private static final Clock CLOCK = Clock.systemUTC();
    private static final ExecutionLimits LIMITS = new ExecutionLimits(30, 2, 3, 20000, 10000);

    private SpecializedAgent contributor(String id, double score, boolean fallback, boolean failFirst) {
        AtomicInteger calls = new AtomicInteger();
        return new SpecializedAgent() {
            public AgentConfig config() {
                return new AgentConfig(id, "controlled expert", "private prompt", Set.of("draft-reply"), List.of(),
                        Set.of(id, RequestSplitter.DEFAULT_ID), fallback);
            }
            public AgentAssessment assess(Task task, BlackboardView view) {
                int feedback = view.snapshot().feedback().size();
                boolean contributed = Contribution.latest(view.snapshot().evidence()).stream()
                        .anyMatch(item -> item.agentId().equals(id) && item.feedbackSeen() == feedback);
                return new AgentAssessment(id, contributed ? 0 : score, true, "Controlled fit");
            }
            public AgentOutcome execute(Task task, AgentBlackboard access) {
                if (failFirst && calls.getAndIncrement() == 0)
                    return new AgentOutcome(TaskStatus.FAILED, "Controlled transient failure", List.of(),
                            Optional.of(new Failure(FailureKind.CONNECTION, "Controlled transient failure", true)));
                BlackboardSnapshot state = access.snapshot();
                int feedback = state.feedback().size();
                ReplyPlan plan = new ReplyPlan("plan-" + id, id, "controlled", "fixed", "en", List.of(), "synthetic",
                        List.of(), List.of(), List.of(), List.of(), List.of());
                Contribution contribution = new Contribution(Contribution.SCHEMA, "contribution:" + id + ":" + (feedback + 1),
                        id, "controlled", score, List.of(1), feedback, plan, List.of());
                access.submitEvidence(List.of(contribution.toEvidence(state.id().scope(), CLOCK.instant())));
                return new AgentOutcome(TaskStatus.SUCCEEDED, "Posted contribution", List.of(), Optional.empty());
            }
        };
    }

    private SpecializedAgent composer() {
        return new SpecializedAgent() {
            public AgentConfig config() {
                return new AgentConfig("composer", "controlled composer", "private composer prompt", Set.of("compose-reply"),
                        List.of(), Set.of("alpha", "beta", "fallback", RequestSplitter.DEFAULT_ID), false);
            }
            public AgentAssessment assess(Task task, BlackboardView view) {
                return new AgentAssessment("composer", Contribution.latest(view.snapshot().evidence()).isEmpty() ? 0 : 1,
                        true, "Contributions exist");
            }
            public AgentOutcome execute(Task task, AgentBlackboard access) {
                access.submitDraft(new DraftProposal("Synthetic subject", "Fixed model response", "composer", "Controlled output"));
                return new AgentOutcome(TaskStatus.SUCCEEDED, "Fixed response stored", List.of(), Optional.empty());
            }
        };
    }

    private void scenario(boolean fallback, boolean failFirst, boolean revise) throws Exception {
        SqliteBlackboardStore store = new SqliteBlackboardStore(root.resolve("state.db"));
        DefaultBlackboardFactory factory = new DefaultBlackboardFactory(store);
        InMemoryAgentRegistry agents = new InMemoryAgentRegistry();
        agents.register(RequestSplitter.withRules());
        agents.register(contributor("alpha", fallback ? 0 : 0.9, false, failFirst));
        agents.register(contributor("beta", fallback ? 0 : 0.8, false, false));
        agents.register(contributor("fallback", 1, true, false));
        agents.register(composer());
        LeasedCoordinator coordinator = new LeasedCoordinator(email -> new Analysis(List.of(), List.of(), Map.of()), factory,
                agents, new ScoreRoutingStrategy(0.4, 0.1), new AgendaStrategy(agents, AgendaStrategy.Settings.defaults()),
                EvidencePromotion.inferencesOnly(), Optional.empty(), Duration.ofMinutes(1));
        Scope scope = new Scope("synthetic-project", "synthetic-thread");
        BoardId id = coordinator.receive(new Email("synthetic-mail", scope, "a@example.test", List.of("b@example.test"),
                "Synthetic request", "Could you provide a form?", CLOCK.instant(), Map.of()));
        BlackboardSnapshot state = coordinator.run(id, LIMITS);
        assertEquals(BoardStatus.AWAITING_REVIEW, state.status(), state.failure().toString());
        if (revise) {
            new BoardReviewService(factory, CLOCK).reject(id, 1, "Please revise the synthetic reply.");
            state = coordinator.run(id, LIMITS);
            assertEquals(2, state.drafts().size());
            assertEquals(ReviewStatus.REJECTED, state.drafts().get(0).review());
        }
        ObservationSource source = new SqliteObservationSource(root.resolve("state.db"), Set.of(boardKey(id)), CLOCK);
        JsonNode view = new ObjectMapper().readTree(JsonCodec.write(new ObservationView(source.capture(boardKey(id)), false)
                .render("*", Instant.EPOCH, CLOCK.instant().plusSeconds(60))));
        assertEquals(store.latest(id).orElseThrow().revision(), view.path("sourceRevision").asLong());
        assertEquals(state.tasks().size(), view.path("overview").get(0).path("taskCount").asInt());
        assertEquals(state.drafts().size(), view.path("overview").get(0).path("draftVersion").asInt());
        assertEquals(state.executions().size(), view.path("events").findValuesAsText("kind").stream().filter("EXECUTION"::equals).count());
        Set<String> contributors = new TreeSet<>();
        view.path("experts").forEach(expert -> { if (expert.path("contributionCount").asInt() > 0) contributors.add(expert.path("agent").asText()); });
        assertEquals(fallback ? Set.of("fallback") : Set.of("alpha", "beta"), contributors);
        long expectedContributions = fallback ? 1 : revise ? 4 : 2;
        assertEquals(expectedContributions, view.path("nodes").findValuesAsText("subTitle").stream().filter("contribution"::equals).count());
        assertEquals(state.drafts().size(), view.path("nodes").findValuesAsText("title").stream().filter(title -> title.startsWith("Draft v")).count());
        assertTrue(view.path("edges").findValuesAsText("mainStat").stream().noneMatch(text -> text.contains("adopted")));
        if (failFirst) assertEquals(1, view.path("overview").get(0).path("failedAttemptCount").asInt());
        Set<String> ids = new HashSet<>();
        view.path("nodes").forEach(node -> assertTrue(ids.add(node.path("id").asText()), "unique stable node IDs"));
        view.path("edges").forEach(edge -> { assertTrue(ids.contains(edge.path("source").asText())); assertTrue(ids.contains(edge.path("target").asText())); });
        JsonNode filtered = new ObjectMapper().readTree(JsonCodec.write(new ObservationView(source.capture(boardKey(id)), false)
                .render("alpha", Instant.EPOCH, CLOCK.instant().plusSeconds(60))));
        filtered.path("experts").forEach(expert -> assertEquals("alpha", expert.path("agent").asText()));
        assertFalse(view.toString().contains("Fixed model response"));
    }

    @Test void multipleExpertsContributeAndFixedModelDraftMatchesTheAuthority() throws Exception { scenario(false, false, false); }
    @Test void noSuitableExpertUsesTheRecordedFallback() throws Exception { scenario(true, false, false); }
    @Test void failedExecutionAndRetryStayDistinct() throws Exception { scenario(false, true, false); }
    @Test void rejectionCreatesSeparateContributionAndDraftVersions() throws Exception { scenario(false, false, true); }

    @Test void containerFixtureRestartPreservesTheSealedAuthority() throws Exception {
        Path directory = root.resolve("sealed-fixture");
        main(new String[]{directory.toString()});
        Path database = directory.resolve("state.db");
        byte[] before = Files.readAllBytes(database);
        String[] keys = JsonCodec.read(Files.readString(directory.resolve("boards.json")), String[].class);
        assertEquals(3, keys.length);
        ObservationSource source = new SqliteObservationSource(database, Set.of(keys), CLOCK);
        assertEquals(3, source.boards().size());
        for (String key : keys) assertEquals(BoardStatus.AWAITING_REVIEW, source.capture(key).latest().snapshot().status());
        main(new String[]{directory.toString()});
        assertArrayEquals(before, Files.readAllBytes(database));
    }

    /** Reusable synthetic workflow fixture for local Grafana verification; never reads a real mailbox. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ObservationWorkflowTest <new-fixture-directory>");
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        if (Files.isRegularFile(directory.resolve("fixture-ready"))) {
            String[] existing = JsonCodec.read(Files.readString(directory.resolve("boards.json")), String[].class);
            System.out.println("SUPERMAS_OBSERVATION_BOARDS=" + String.join(",", existing));
            return;
        }
        if (Files.exists(directory.resolve("state.db"))) throw new IllegalArgumentException("Fixture database already exists");
        ObservationWorkflowTest fixture = new ObservationWorkflowTest();
        fixture.root = directory;
        fixture.scenario(false, false, false);
        fixture.scenario(true, false, false);
        fixture.scenario(false, true, true);
        SqliteBlackboardStore store = new SqliteBlackboardStore(directory.resolve("state.db"));
        List<String> keys = store.boards().stream().map(org.supermas.blackboard.persistence.BlackboardKeys::boardKey).toList();
        Files.writeString(directory.resolve("boards.json"), JsonCodec.pretty(keys));
        Files.writeString(directory.resolve("boards.env"), "SUPERMAS_OBSERVATION_BOARDS=" + String.join(",", keys) + "\n");
        // Seal this offline synthetic fixture for a read-only container mount, without WAL side files.
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("state.db"));
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=DELETE");
        }
        Files.writeString(directory.resolve("fixture-ready"), "synthetic-workflow/v1\n");
        System.out.println("SUPERMAS_OBSERVATION_BOARDS=" + String.join(",", keys));
    }
}
