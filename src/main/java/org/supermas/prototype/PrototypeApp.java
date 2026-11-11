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
import org.supermas.llm.LlmGenerationService;
import org.supermas.llm.LlmSettings;
import org.supermas.llm.LocalLlm;
import org.supermas.llm.OpenAiCompatibleLlm;
import org.supermas.llm.RecordedLlm;
import org.supermas.model.Domain.ModelConfig;
import org.supermas.control.AgendaStrategy;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.coordinator.Coordinator;
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
import org.supermas.memory.SubjectThreadFallback;
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
    /** A local model on modest hardware can be slow; the composer makes one call per draft version. */
    static final Duration LLM_TIMEOUT = Duration.ofMinutes(3);
    /** An online service answers in seconds; with one retry, two attempts stay well inside the 5-minute lease. */
    static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(60);
    static final int LLM_MAX_OUTPUT = 8_000;
    /** Discovered topics are rebuilt after this many new received or sent messages. */
    static final int REGROUP_EVERY = 25;

    private PrototypeApp() { }

    /**
     * @param minContribution the blackboard's lowest specialist estimate for a contribution
     * @param llm             which engine writes the replies (template by default; see {@link LlmSettings})
     */
    record Options(Path emails, Path knowledge, Path data, String project, Architecture architecture,
                   double minContribution, LlmSettings llm, java.util.Set<String> owners) {
        Options {
            owners = java.util.Set.copyOf(owners);
        }

        /** Without owner addresses: mail is never recognised as the owner's own. */
        Options(Path emails, Path knowledge, Path data, String project, Architecture architecture, double minContribution,
                LlmSettings llm) {
            this(emails, knowledge, data, project, architecture, minContribution, llm, java.util.Set.of());
        }

        Options(Path emails, Path knowledge, Path data, String project, Architecture architecture, double minContribution) {
            this(emails, knowledge, data, project, architecture, minContribution, LlmSettings.template());
        }

        Options(Path emails, Path knowledge, Path data, String project) {
            this(emails, knowledge, data, project, Architecture.BLACKBOARD);
        }

        Options(Path emails, Path knowledge, Path data, String project, Architecture architecture) {
            this(emails, knowledge, data, project, architecture, AgendaStrategy.Settings.defaults().minContribution());
        }

        /** Options from flags only; the model settings ignore the environment (for tests). */
        static Options parse(String[] args) {
            return parse(args, Map.of());
        }

        /** Options from flags, with the model settings also read from {@code environment} (flags win). */
        static Options parse(String[] args, Map<String, String> environment) {
            if (args.length == 0) {
                throw new IllegalArgumentException("Usage: PrototypeApp EMAILS [--knowledge DIR] [--data DIR] [--project ID]"
                        + " [--architecture blackboard|router] [--min-contribution 0..1]"
                        + " [--llm-provider template|local|online|replay] [--llm-endpoint URL] [--llm-model NAME]"
                        + " [--llm-cache DIR] [--owner ADDRESS,...]");
            }
            Architecture architecture = Architecture.BLACKBOARD;
            double minContribution = AgendaStrategy.Settings.defaults().minContribution();
            Map<String, String> llmFlags = new HashMap<>();
            Path emails = Path.of(args[0]);
            Path knowledge = Path.of("src/test/resources/university/knowledge");
            Path data = Path.of("runtime");
            String project = "uni";
            String ownerFlag = null;
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
                    case "--owner" -> ownerFlag = args[i + 1];
                    default -> {
                        if (!LlmSettings.isFlag(args[i])) {
                            throw new IllegalArgumentException("Unknown option " + args[i]);
                        }
                        llmFlags.put(args[i], args[i + 1]);
                    }
                }
            }
            // Owner addresses: the flag wins over SUPERMAS_OWNER; mail they sent needs no reply (plan reply-needed).
            String owners = ownerFlag != null ? ownerFlag : environment.getOrDefault("SUPERMAS_OWNER", "");
            return new Options(emails, knowledge, data, project, architecture, minContribution,
                    LlmSettings.from(environment, llmFlags), Wiring.owners(owners));
        }
    }

    /** What happened to one email, for the summary and for tests. */
    record Outcome(Path file, BoardId board, BlackboardSnapshot state) { }

    public static void main(String[] args) throws Exception {
        run(Options.parse(args, System.getenv()), System.out);
    }

    static List<Outcome> run(Options options, PrintStream out) throws IOException {
        List<Path> files = emailFiles(options.emails());
        Files.createDirectories(options.data());
        List<Outcome> outcomes = new ArrayList<>();
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(options.data().resolve("index"))) {
            Wiring wiring = Wiring.create(indexes, options);
            out.println("Model: " + options.llm().describe());
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
            for (String part : String.valueOf(draft.explanation()).split("; ")) {
                if (part.startsWith(org.supermas.llm.DecisionPhrases.WARNING_PREFIX)) {
                    out.println("  ! " + part);
                }
            }
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
        final Coordinator coordinator;
        final ConversationRecorder recorder;
        final RoleCatalog roles;
        final CoordinationStrategy strategy;

        private Wiring(Map<String, ContextSource> sources, TopicCatalog catalog, InMemoryAgentRegistry registry,
                       SqliteBlackboardStore store, Coordinator coordinator, ConversationRecorder recorder,
                       RoleCatalog roles, CoordinationStrategy strategy) {
            this.strategy = strategy;
            this.sources = sources;
            this.catalog = catalog;
            this.registry = registry;
            this.recorder = recorder;
            this.roles = roles;
            this.store = store;
            this.coordinator = coordinator;
        }

        /** Owner addresses from a comma-separated list, lower-case. */
        static java.util.Set<String> owners(String value) {
            return java.util.Arrays.stream(value.split(",")).map(String::strip).filter(a -> !a.isEmpty())
                    .map(a -> a.toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        /** The engine the settings name; replacing the model or the engine is configuration only. */
        static RoleServices withGeneration(RoleServices services, LlmSettings llm) {
            if (llm.provider() == LlmSettings.Provider.TEMPLATE) {
                return services.withGeneration(new TemplateGenerationService(), RoleServices.UNCONFIGURED_MODEL);
            }
            LocalLlm engine = switch (llm.provider()) {
                case LOCAL -> new OpenAiCompatibleLlm();
                case ONLINE -> OpenAiCompatibleLlm.online(llm.apiKey().orElseThrow());
                case REPLAY -> RecordedLlm.replay(llm.cache().orElseThrow());
                default -> throw new IllegalStateException("Unhandled provider " + llm.provider());
            };
            if (llm.provider() != LlmSettings.Provider.REPLAY && llm.cache().isPresent()) {
                engine = RecordedLlm.recording(engine, llm.cache().get());
            }
            String label = switch (llm.provider()) {
                case ONLINE -> "Online model (development stand-in)";
                case REPLAY -> "Recorded model";
                default -> "Local model";
            };
            return services.withGeneration(new LlmGenerationService(engine, label),
                    new ModelConfig(llm.model(), llm.endpoint(),
                            llm.provider() == LlmSettings.Provider.ONLINE ? ONLINE_TIMEOUT : LLM_TIMEOUT, LLM_MAX_OUTPUT));
        }

        static Wiring create(ProjectIndexes indexes, Options options) {
            return create(indexes, options, RoleCatalog.bundled());
        }

        /** Batch wiring: a board whose email needs no reply stops without a draft (plan reply-needed). */
        static Wiring create(ProjectIndexes indexes, Options options, RoleCatalog roles) {
            return create(indexes, options, roles, true);
        }

        /** With a given role catalogue, e.g. one with a composer prompt variant (benchmarks). */
        /**
         * @param batch true for runs without a person asking (the {@code .eml} prototype, benchmarks): boards of emails
         *              that need no reply stop before any work. The agent service passes false: a person asked.
         */
        static Wiring create(ProjectIndexes indexes, Options options, RoleCatalog roles, boolean batch) {
            String project = options.project();
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

            RoleServices services = withGeneration(RoleServices.planOnly().withRetriever(retriever), options.llm());
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
            if (batch) {
                strategy = new org.supermas.control.ReplyNeedGate(strategy);
            }
            LeasedCoordinator coordinator = new LeasedCoordinator(MetadataAgent.rulesOnly().withOwners(options.owners()),
                    new DefaultBlackboardFactory(store), registry, new ScoreRoutingStrategy(0.4, 0.1), strategy,
                    new EvidencePromotion(retriever::readers), Optional.of(recorder), Duration.ofMinutes(5));
            // Topics from mail already stored by an earlier run, before the first new email.
            maintainer.refreshNow(project);
            // A reply without reply headers joins its earlier thread only within the same participants (B47).
            Coordinator receiving = new ThreadResolvingCoordinator(coordinator, new SubjectThreadFallback(indexes));
            return new Wiring(sources, catalog, registry, store, receiving, recorder, roles, strategy);
        }
    }
}
