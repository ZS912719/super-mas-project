package org.supermas.prototype;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.supermas.agent.collaboration.RequestSplitter;
import org.supermas.agent.role.ComposerAgent;
import org.supermas.agent.role.ReplyPlan;
import org.supermas.agent.role.RoleAgent;
import org.supermas.agent.role.RoleAgents;
import org.supermas.agent.role.RoleCatalog;
import org.supermas.agent.role.RoleServices;
import org.supermas.blackboard.export.MarkdownProjector;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;
import org.supermas.context.DiscoveredTopicsAgent;
import org.supermas.control.AgendaStrategy;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.context.LuceneContextRetriever;
import org.supermas.context.MissingInformationAgent;
import org.supermas.context.ProjectIndexes;
import org.supermas.context.RuleMissingInformationCheck;
import org.supermas.context.TopicCandidateFilter;
import org.supermas.context.TopicCatalog;
import org.supermas.context.TopicContextAgent;
import org.supermas.context.TopicDefinition;
import org.supermas.context.TopicDiscovery;
import org.supermas.context.TopicRegrouper;
import org.supermas.context.TopicType;
import org.supermas.memory.AutoTopicMaintainer;
import org.supermas.memory.ConversationRecorder;
import org.supermas.memory.IndexConversationRepository;
import org.supermas.memory.KnowledgeLoader;
import org.supermas.memory.MessageTagger;
import org.supermas.metadata.EmailParseException;
import org.supermas.metadata.MetadataAgent;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.RoutingDecision;

// PROTOTYPE-WIRING: the first runnable prototype on the prototype branch. It joins this member's part
// (metadata, memory, topics, context, specialized agents) with codex/blackboard-storage (SQLite blackboard and
// Markdown observation), and uses stand-ins for the parts nobody has built yet: coordinator, routing,
// scheduling and generation. Nothing is approved or sent. Replace the stand-ins as the team delivers them.
/**
 * Reads emails from {@code .eml} files and takes each one to a draft awaiting review on the durable blackboard.
 *
 * <p>Usage: {@code PrototypeApp EMAILS [--knowledge DIR] [--data DIR] [--project ID]}
 *
 * <ul>
 *   <li>{@code EMAILS}: one {@code .eml} file, or a folder whose {@code .eml} files are read in name order.</li>
 *   <li>{@code --knowledge}: a knowledge folder with {@code sources.json} ({@link KnowledgeLoader}). Default: the
 *       sample university knowledge in {@code src/test/resources/university/knowledge}.</li>
 *   <li>{@code --data}: where state is kept between runs. Default {@code runtime}, which Git ignores:
 *       {@code blackboard.db} (SQLite), {@code super-mas-vault} (Markdown for Obsidian), {@code index} (Lucene:
 *       knowledge and conversation history) and {@code topics} (discovered topics).</li>
 *   <li>{@code --project}: the project the emails belong to. Default {@code uni}, as in the sample knowledge.</li>
 * </ul>
 *
 * <p>Drafts are written by {@link TemplateGenerationService}, a fixed template from the reply plan with no
 * language model. A local model is the intended next step and needs only another {@code GenerationService}.
 */
public final class PrototypeApp {
    static final String DISCUSSION = "context-discussion";
    static final String DISCOVERED = "context-discovered";
    static final String MAIL = "mail";
    /** Router baseline: route, generate, present. */
    static final ExecutionLimits ROUTER_LIMITS = new ExecutionLimits(12, 1, 1, 50_000, 20_000);
    /** Blackboard: more cycles (requests, context, several contributions, compose) and room for two revisions. */
    static final ExecutionLimits BLACKBOARD_LIMITS = new ExecutionLimits(24, 1, 3, 50_000, 20_000);

    /** Which organization runs: the Phase 12 blackboard (default) or the step 11 router, kept as a baseline. */
    public enum Architecture {
        BLACKBOARD, ROUTER;

        ExecutionLimits limits() {
            return this == BLACKBOARD ? BLACKBOARD_LIMITS : ROUTER_LIMITS;
        }
    }
    /** Discovered topics are rebuilt after this many new received or sent messages. */
    static final int REGROUP_EVERY = 25;

    private PrototypeApp() { }

    /** @param minContribution the blackboard's lowest specialist estimate for a contribution */
    record Options(Path emails, Path knowledge, Path data, String project, Architecture architecture,
                   double minContribution) {
        Options(Path emails, Path knowledge, Path data, String project) {
            this(emails, knowledge, data, project, Architecture.BLACKBOARD);
        }

        Options(Path emails, Path knowledge, Path data, String project, Architecture architecture) {
            this(emails, knowledge, data, project, architecture, AgendaStrategy.Settings.defaults().minContribution());
        }

        static Options parse(String[] args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("Usage: PrototypeApp EMAILS [--knowledge DIR] [--data DIR] [--project ID]"
                        + " [--architecture blackboard|router] [--min-contribution 0..1]");
            }
            Architecture architecture = Architecture.BLACKBOARD;
            double minContribution = AgendaStrategy.Settings.defaults().minContribution();
            Path emails = Path.of(args[0]);
            Path knowledge = Path.of("src/test/resources/university/knowledge");
            Path data = Path.of("runtime");
            String project = "uni";
            for (int i = 1; i < args.length; i += 2) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + args[i]);
                }
                switch (args[i]) {
                    case "--knowledge" -> knowledge = Path.of(args[i + 1]);
                    case "--data" -> data = Path.of(args[i + 1]);
                    case "--project" -> project = args[i + 1];
                    case "--architecture" -> architecture = Architecture.valueOf(args[i + 1].toUpperCase(java.util.Locale.ROOT));
                    case "--min-contribution" -> minContribution = Double.parseDouble(args[i + 1]);
                    default -> throw new IllegalArgumentException("Unknown option " + args[i]);
                }
            }
            return new Options(emails, knowledge, data, project, architecture, minContribution);
        }
    }

    /** What happened to one email, for the summary and for tests. */
    record Outcome(Path file, BoardId board, BlackboardSnapshot state) { }

    public static void main(String[] args) throws Exception {
        run(Options.parse(args), System.out);
    }

    static List<Outcome> run(Options options, PrintStream out) throws IOException {
        List<Path> files = emailFiles(options.emails());
        Files.createDirectories(options.data());
        List<Outcome> outcomes = new ArrayList<>();
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(options.data().resolve("index"))) {
            Wiring wiring = Wiring.create(indexes, options);
            out.println("Knowledge sources: " + wiring.sources.keySet());
            out.println("Agents: " + wiring.registry.all().stream().map(agent -> agent.config().id()).toList());
            out.println("Discovered topics at start: " + wiring.catalog.topics(options.project()).size());
            MimeEmailParser parser = new MimeEmailParser();
            for (Path file : files) {
                Email email;
                try {
                    email = parser.parse(Files.readAllBytes(file), options.project());
                } catch (EmailParseException e) {
                    out.println("\n== " + file.getFileName() + ": not a readable email (" + e.getMessage() + ")");
                    continue;
                }
                BoardId board = wiring.coordinator.receive(email);
                BlackboardSnapshot state = wiring.coordinator.run(board, options.architecture().limits());
                outcomes.add(new Outcome(file, board, state));
                print(out, file, state);
            }
            try (MarkdownProjector projector = new MarkdownProjector(wiring.store, options.data().resolve("super-mas-vault"))) {
                projector.reconcile();
                out.println("\nObsidian vault: " + options.data().resolve("super-mas-vault").toAbsolutePath());
                for (Outcome outcome : outcomes) {
                    out.println("  " + outcome.file().getFileName() + " -> "
                            + projector.boardDirectory(outcome.board()).resolve("current.md").toAbsolutePath());
                }
            }
            out.println("Discovered topics at end: " + wiring.catalog.topics(options.project()).size());
        }
        return outcomes;
    }

    private static List<Path> emailFiles(Path emails) throws IOException {
        if (Files.isRegularFile(emails)) {
            return List.of(emails);
        }
        try (Stream<Path> listing = Files.list(emails)) {
            return listing.filter(path -> path.getFileName().toString().endsWith(".eml")).sorted().toList();
        }
    }

    private static void print(PrintStream out, Path file, BlackboardSnapshot state) {
        out.println("\n== " + file.getFileName() + ": " + state.email().subject());
        out.println("Status: " + state.status() + state.failure().map(failure -> " (" + failure.kind() + ": "
                + failure.message() + ")").orElse(""));
        state.routing().ifPresent(routing -> out.println("Routing: " + describe(routing)));
        state.evidence().stream().map(ReplyPlan::from).flatMap(Optional::stream).reduce((first, second) -> second)
                .ifPresent(plan -> out.println("Plan: requests " + plan.requests() + ", missing " + plan.missing()
                        + ", " + plan.context().size() + " context items"));
        out.println("Steps:");
        for (org.supermas.model.Domain.ExecutionRecord step : state.executions()) {
            String agent = state.tasks().stream().filter(task -> task.id().equals(step.taskId())).findFirst()
                    .flatMap(org.supermas.model.Domain.Task::agentId).orElse("-");
            String why = step.selectionReason().length() > 150 ? step.selectionReason().substring(0, 150) + "…"
                    : step.selectionReason();
            out.println("  " + agent + ": " + step.summary() + "  [" + why + "]");
        }
        if (!state.drafts().isEmpty()) {
            Draft draft = state.drafts().get(state.drafts().size() - 1);
            out.println("Draft v" + draft.version() + " by " + draft.agentId() + " (" + draft.review() + "):");
            draft.body().lines().forEach(line -> out.println("  | " + line));
        }
    }

    private static String describe(RoutingDecision routing) {
        return routing.match() + routing.selectedAgentId().map(id -> " -> " + id).orElse("") + ", "
                + routing.candidates().stream().filter(candidate -> candidate.score() > 0)
                .map(candidate -> candidate.agentId() + "=" + String.format(java.util.Locale.ROOT, "%.2f",
                        candidate.score())).toList();
    }

    /** Everything wired together, as {@code EndToEndTest} wires it, on durable storage. */
    static final class Wiring {
        final Map<String, ContextSource> sources;
        final TopicCatalog catalog;
        final InMemoryAgentRegistry registry;
        final SqliteBlackboardStore store;
        final LeasedCoordinator coordinator;
        final ConversationRecorder recorder;
        final RoleCatalog roles;

        private Wiring(Map<String, ContextSource> sources, TopicCatalog catalog, InMemoryAgentRegistry registry,
                       SqliteBlackboardStore store, LeasedCoordinator coordinator, ConversationRecorder recorder,
                       RoleCatalog roles) {
            this.sources = sources;
            this.catalog = catalog;
            this.registry = registry;
            this.recorder = recorder;
            this.roles = roles;
            this.store = store;
            this.coordinator = coordinator;
        }

        static Wiring create(ProjectIndexes indexes, Options options) {
            String project = options.project();
            RoleCatalog roles = RoleCatalog.bundled();
            Map<String, ContextSource> sources = new HashMap<>(new KnowledgeLoader(indexes).load(options.knowledge()).sources());
            // Every agent that should read mail is a reader of the mail source, in trusted configuration.
            Set<String> mailReaders = new LinkedHashSet<>(List.of(DISCUSSION, DISCOVERED));
            roles.roles().forEach(role -> mailReaders.add(role.id()));
            sources.put(MAIL, new ContextSource(MAIL, project, URI.create("file:///mail"), Set.copyOf(mailReaders)));

            Map<String, AgentConfig> configs = new HashMap<>();
            roles.all().forEach(config -> configs.put(config.id(), config));
            LuceneContextRetriever retriever = new LuceneContextRetriever(indexes, sources,
                    id -> Optional.ofNullable(configs.get(id)));

            TopicCatalog catalog = TopicCatalog.onDisk(options.data().resolve("topics"));
            IndexConversationRepository repository = new IndexConversationRepository(indexes,
                    id -> Optional.ofNullable(sources.get(MAIL)).filter(source -> source.projectId().equals(id)));
            ConversationRecorder recorder = new ConversationRecorder(repository, MessageTagger.discovered(catalog));
            AutoTopicMaintainer maintainer = new AutoTopicMaintainer(
                    new TopicRegrouper(indexes, catalog, new TopicDiscovery(TopicDiscovery.Settings.defaults())), REGROUP_EVERY);
            recorder.addListener(maintainer);

            TopicContextAgent discussion = new TopicContextAgent(new TopicDefinition(TopicType.DISCUSSION, DISCUSSION,
                    project, "", List.of(), Set.of(MAIL), 4000), retriever);
            AgentConfig discoveredConfig = new AgentConfig(DISCOVERED, "context-discovered", "",
                    Set.of(TopicContextAgent.CAPABILITY), List.of(), Set.of(MAIL), false);
            DiscoveredTopicsAgent discovered = new DiscoveredTopicsAgent(discoveredConfig, project, catalog, retriever, 2, 4000);
            MissingInformationAgent check = new MissingInformationAgent(MissingInformationAgent.DEFAULT_ID,
                    new RuleMissingInformationCheck());
            configs.put(DISCUSSION, discussion.config());
            configs.put(DISCOVERED, discoveredConfig);
            configs.put(check.config().id(), check.config());

            RoleServices services = RoleServices.planOnly().withRetriever(retriever)
                    .withGeneration(new TemplateGenerationService(), RoleServices.UNCONFIGURED_MODEL);
            SqliteBlackboardStore store = new SqliteBlackboardStore(options.data().resolve("blackboard.db"));
            InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
            boolean blackboard = options.architecture() == Architecture.BLACKBOARD;
            if (blackboard) {
                registry.register(RequestSplitter.withRules());
            }
            registry.register(discussion);
            registry.register(discovered);
            registry.register(check);
            for (RoleAgent agent : RoleAgents.create(roles, services)) {
                registry.register(agent);
            }
            CoordinationStrategy strategy;
            if (blackboard) {
                registry.register(new ComposerAgent(ComposerAgent.DEFAULT_ID, roles, services, RequestSplitter.DEFAULT_ID));
                AgendaStrategy.Settings defaults = AgendaStrategy.Settings.defaults();
                strategy = new AgendaStrategy(registry, new AgendaStrategy.Settings(options.minContribution(),
                        defaults.lowerLevelWeight(), defaults.reservedRounds()));
            } else {
                strategy = new ContextFirstCoordinationStrategy(List.of(discussion), List.of(discovered),
                        new TopicCandidateFilter(3), Optional.of(check.config().id()), new DraftFirstCoordinationStrategy(), 2);
            }
            LeasedCoordinator coordinator = new LeasedCoordinator(MetadataAgent.rulesOnly(),
                    new DefaultBlackboardFactory(store), registry, new ScoreRoutingStrategy(0.4, 0.1), strategy,
                    new EvidencePromotion(retriever::readers), Optional.of(recorder), Duration.ofMinutes(5));
            // Topics from mail already stored by an earlier run, before the first new email.
            maintainer.refreshNow(project);
            return new Wiring(sources, catalog, registry, store, coordinator, recorder, roles);
        }
    }
}
