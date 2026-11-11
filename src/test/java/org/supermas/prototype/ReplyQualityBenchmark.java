package org.supermas.prototype;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.supermas.context.ProjectIndexes;
import org.supermas.llm.LlmSettings;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.Email;

/**
 * Step 13: the quality of the replies, for one model configuration, on the 24 single-intent and the 12
 * multi-request synthetic emails, through the prototype's real wiring (blackboard architecture, SQLite board).
 * Each email's reply is scored by {@link ReplyChecks} against its frozen {@code <email>.reply.json}.
 *
 * <p>Each set runs on a fresh data folder per repeat, emails in file order (so a thread's later message sees the
 * earlier one, as in real use). Every model reply is recorded per set and repeat under the cache folder, so the
 * same run can be replayed without a model ({@code --llm-provider replay} with the same cache folder).
 *
 * <p>Usage: {@code ReplyQualityBenchmark --label NAME [--out DIR] [--dev-repeats N] [--multi-repeats N]
 * [--llm-provider ...] [--llm-model ...] [--llm-endpoint ...] [--llm-cache DIR]}, model settings also from the
 * {@code SUPERMAS_LLM_*} variables. Writes {@code results.md}, {@code drafts.md} and {@code scores.jsonl} to
 * {@code OUT/LABEL}. It refuses an existing label folder, so no run is mixed with an earlier one.
 */
public final class ReplyQualityBenchmark {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path KNOWLEDGE = Path.of("src/test/resources/university/knowledge");
    private static final Pattern PROMPT = Pattern.compile("prompt ([0-9a-f]{12})");

    private ReplyQualityBenchmark() { }

    record Item(String set, String name, Email email, ReplyChecks.Gold gold) { }

    record Result(Item item, int repeat, BoardStatus status, String failure, String body, String explanation,
                  ReplyChecks.Score score, double millis) {
        boolean drafted() {
            return status == BoardStatus.AWAITING_REVIEW && !body.isEmpty();
        }
    }

    /** @param rescore an earlier run's label folder whose saved replies are scored again, with no model */
    /**
     * @param composerPrompt a composer prompt variant to use instead of the bundled one
     * @param architecture   blackboard (default) or the step 11 router, for the ablation W3
     * @param knowledge      the knowledge folder; a folder with an empty {@code sources.json} runs without knowledge
     * @param singleAgent    instead of the multi-agent system: one model call per email with this prompt (after
     *                       {@code common.txt}), the email and every knowledge document (benchmark plan W2)
     */
    record Options(String label, Path out, int devRepeats, int multiRepeats, Path dev, Path multi, LlmSettings llm,
                   java.util.Optional<Path> rescore, java.util.Optional<Path> composerPrompt,
                   PrototypeApp.Architecture architecture, Path knowledge, java.util.Optional<Path> singleAgent) { }

    public static void main(String[] args) throws Exception {
        Options options = parse(args, System.getenv());
        Path folder = run(options);
        System.out.println("Results in " + folder.resolve("results.md"));
    }

    static Options parse(String[] args, Map<String, String> environment) {
        String label = null;
        Path out = Path.of("runtime/reply-benchmark");
        int devRepeats = 1;
        int multiRepeats = 2;
        Path dev = Path.of("benchmark/data/synthetic-dev");
        Path multi = Path.of("benchmark/data/synthetic-multi");
        Map<String, String> llmFlags = new HashMap<>();
        java.util.Optional<Path> rescore = java.util.Optional.empty();
        java.util.Optional<Path> composerPrompt = java.util.Optional.empty();
        PrototypeApp.Architecture architecture = PrototypeApp.Architecture.BLACKBOARD;
        Path knowledge = KNOWLEDGE;
        java.util.Optional<Path> singleAgent = java.util.Optional.empty();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + args[i]);
            }
            switch (args[i]) {
                case "--label" -> label = args[i + 1];
                case "--out" -> out = Path.of(args[i + 1]);
                case "--dev-repeats" -> devRepeats = Integer.parseInt(args[i + 1]);
                case "--multi-repeats" -> multiRepeats = Integer.parseInt(args[i + 1]);
                case "--dev" -> dev = Path.of(args[i + 1]);
                case "--multi" -> multi = Path.of(args[i + 1]);
                case "--rescore" -> rescore = java.util.Optional.of(Path.of(args[i + 1]));
                case "--composer-prompt" -> composerPrompt = java.util.Optional.of(Path.of(args[i + 1]));
                case "--architecture" -> architecture = PrototypeApp.Architecture.valueOf(args[i + 1].toUpperCase(Locale.ROOT));
                case "--knowledge" -> knowledge = Path.of(args[i + 1]);
                case "--single-agent" -> singleAgent = java.util.Optional.of(Path.of(args[i + 1]));
                default -> {
                    if (!LlmSettings.isFlag(args[i])) {
                        throw new IllegalArgumentException("Unknown option " + args[i]);
                    }
                    llmFlags.put(args[i], args[i + 1]);
                }
            }
        }
        if (label == null || !label.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Give --label NAME (letters, digits, '.', '_', '-'), e.g. --label A-3b");
        }
        return new Options(label, out, devRepeats, multiRepeats, dev, multi, LlmSettings.from(environment, llmFlags), rescore,
                composerPrompt, architecture, knowledge, singleAgent);
    }

    /** Runs every set and repeat, writes the results, and returns the label folder. */
    static Path run(Options options) throws IOException {
        Path folder = options.out().resolve(options.label());
        if (Files.exists(folder)) {
            throw new IllegalArgumentException(folder + " exists; choose a new --label so runs are never mixed");
        }
        Files.createDirectories(folder);
        List<Result> results = new ArrayList<>();
        if (options.rescore().isPresent()) {
            results.addAll(rescore(options));
            write(options, folder, results);
            return folder;
        }
        Path cacheBase = options.llm().cache().orElse(folder.resolve("llm-cache"));
        System.out.println("Model: " + options.llm().describe());
        for (String set : List.of("dev", "multi")) {
            List<Item> items = load(set, set.equals("dev") ? options.dev() : options.multi());
            int repeats = set.equals("dev") ? options.devRepeats() : options.multiRepeats();
            for (int repeat = 1; repeat <= repeats; repeat++) {
                LlmSettings llm = options.llm().withCache(cacheBase.resolve(set + "-r" + repeat));
                System.out.println("Running " + set + " (" + items.size() + " emails), repeat " + repeat + " of " + repeats);
                results.addAll(options.singleAgent().isPresent()
                        ? runSingleAgent(items, repeat, llm, options.knowledge(), Files.readString(options.singleAgent().get()))
                        : runSet(items, repeat, llm, folder.resolve("work").resolve(set + "-r" + repeat), roles(options),
                        options.architecture(), options.knowledge()));
            }
        }
        write(options, folder, results);
        return folder;
    }

    private static void write(Options options, Path folder, List<Result> results) throws IOException {
        Files.writeString(folder.resolve("results.md"), render(options, results));
        Files.writeString(folder.resolve("drafts.md"), drafts(options, results));
        StringBuilder lines = new StringBuilder();
        for (Result result : results) {
            lines.append(JSON.writeValueAsString(json(result))).append('\n');
        }
        Files.writeString(folder.resolve("scores.jsonl"), lines);
    }

    /** The saved replies of an earlier run, scored with the current expectations and checks. */
    private static List<Result> rescore(Options options) throws IOException {
        Map<String, Item> items = new HashMap<>();
        for (Item item : load("dev", options.dev())) {
            items.put("dev/" + item.name(), item);
        }
        for (Item item : load("multi", options.multi())) {
            items.put("multi/" + item.name(), item);
        }
        List<Result> results = new ArrayList<>();
        for (String line : Files.readAllLines(options.rescore().get().resolve("scores.jsonl"))) {
            com.fasterxml.jackson.databind.JsonNode saved = JSON.readTree(line);
            Item item = items.get(saved.path("set").asText() + "/" + saved.path("email").asText());
            if (item == null) {
                throw new IOException("No email " + saved.path("email").asText() + " in set " + saved.path("set").asText());
            }
            String body = saved.path("body").asText();
            results.add(new Result(item, saved.path("repeat").asInt(), BoardStatus.valueOf(saved.path("status").asText()),
                    saved.path("failure").asText(), body, saved.path("explanation").asText(),
                    ReplyChecks.score(item.gold(), body), saved.path("millis").asDouble()));
        }
        return results;
    }

    static List<Item> load(String set, Path folder) throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(folder)) {
            files = listing.filter(path -> path.toString().endsWith(".eml")).sorted().toList();
        }
        MimeEmailParser parser = new MimeEmailParser();
        List<Item> items = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".eml", "");
            Path gold = file.resolveSibling(name + ".reply.json");
            if (!Files.exists(gold)) {
                throw new IOException("No reply checks for " + file + " (expected " + gold.getFileName() + ")");
            }
            try {
                items.add(new Item(set, name, parser.parse(Files.readAllBytes(file), "uni"), ReplyChecks.load(gold)));
            } catch (org.supermas.metadata.EmailParseException e) {
                throw new IOException("Cannot parse " + file, e);
            }
        }
        return items;
    }

    private static org.supermas.agent.role.RoleCatalog roles(Options options) throws IOException {
        org.supermas.agent.role.RoleCatalog bundled = org.supermas.agent.role.RoleCatalog.bundled();
        return options.composerPrompt().isEmpty() ? bundled
                : bundled.withComposerPrompt(Files.readString(options.composerPrompt().get()));
    }

    private static List<Result> runSet(List<Item> items, int repeat, LlmSettings llm, Path data,
                                       org.supermas.agent.role.RoleCatalog roles, PrototypeApp.Architecture architecture,
                                       Path knowledge) throws IOException {
        Files.createDirectories(data);
        List<Result> results = new ArrayList<>();
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(data.resolve("index"))) {
            PrototypeApp.Wiring wiring = PrototypeApp.Wiring.create(indexes, new PrototypeApp.Options(null, knowledge, data,
                    "uni", architecture, org.supermas.control.AgendaStrategy.Settings.defaults()
                    .minContribution(), llm), roles);
            for (Item item : items) {
                long start = System.nanoTime();
                BlackboardSnapshot state = wiring.coordinator.run(wiring.coordinator.receive(item.email()),
                        architecture.limits());
                double millis = (System.nanoTime() - start) / 1e6;
                Draft last = state.drafts().isEmpty() ? null : state.drafts().get(state.drafts().size() - 1);
                String body = last == null ? "" : last.body();
                String failure = state.failure().map(found -> found.kind() + ": " + found.message()).orElse("");
                results.add(new Result(item, repeat, state.status(), failure, body, last == null ? "" : last.explanation(),
                        ReplyChecks.score(item.gold(), body), millis));
                System.out.println("  " + item.name() + ": " + state.status() + String.format(Locale.ROOT, " (%.0f ms)", millis));
            }
        }
        return results;
    }

    /**
     * The single-agent baseline (plan W2): no metadata, routing, retrieval or blackboard. One request per email with
     * {@code common.txt} plus {@code prompt}, the email and every knowledge document, through the same output guards
     * ({@link org.supermas.llm.LlmGenerationService}) and the same checks as the system.
     */
    private static List<Result> runSingleAgent(List<Item> items, int repeat, LlmSettings llm, Path knowledge, String prompt)
            throws IOException {
        String common;
        try (var in = ReplyQualityBenchmark.class.getResourceAsStream("/prompts/common.txt")) {
            common = new String(java.util.Objects.requireNonNull(in, "prompts/common.txt").readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        String systemPrompt = common.strip() + "\n\n" + prompt.strip() + "\n";
        List<org.supermas.model.Domain.Evidence> documents = new ArrayList<>();
        try (Stream<Path> files = Files.walk(knowledge)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                String source = file.getParent().getFileName().toString();
                documents.add(new org.supermas.model.Domain.Evidence("knowledge:" + source + "/" + file.getFileName(),
                        null, org.supermas.model.Domain.EvidenceKind.RETRIEVED_EVIDENCE,
                        new org.supermas.model.Domain.Provenance(source, "From knowledge source " + source, null),
                        Files.readString(file), java.util.Set.of()));
            }
        }
        org.supermas.llm.LlmGenerationService generation = new org.supermas.llm.LlmGenerationService(
                BenchmarkEngines.engine(llm), BenchmarkEngines.label(llm));
        org.supermas.model.Domain.ModelConfig config = BenchmarkEngines.config(llm);
        org.supermas.metadata.QuoteSignatureStripper stripper = new org.supermas.metadata.QuoteSignatureStripper();
        int budget = org.supermas.agent.role.RoleCatalog.bundled().roles().stream()
                .mapToInt(org.supermas.agent.role.RoleDefinition::maxInputCharacters).max().orElse(12_000);
        List<Result> results = new ArrayList<>();
        for (Item item : items) {
            long start = System.nanoTime();
            org.supermas.model.Domain.ModelRequest request = org.supermas.agent.role.SingleAgentPrompt.request(systemPrompt,
                    item.email(), stripper.currentText(item.email().body()), documents, budget);
            org.supermas.model.Domain.GenerationResult generated = generation.generate(request, config);
            double millis = (System.nanoTime() - start) / 1e6;
            String body = generated.draft().map(org.supermas.model.Domain.DraftProposal::body).orElse("");
            String failure = generated.failure().map(found -> "SINGLE_AGENT: " + found.message()).orElse("");
            BoardStatus status = generated.draft().isPresent() ? BoardStatus.AWAITING_REVIEW : BoardStatus.FAILED;
            results.add(new Result(item, repeat, status, failure, body,
                    generated.draft().map(org.supermas.model.Domain.DraftProposal::explanation).orElse(""),
                    ReplyChecks.score(item.gold(), body), millis));
            System.out.println("  " + item.name() + ": " + status + String.format(Locale.ROOT, " (%.0f ms)", millis));
        }
        return results;
    }

    // ---- reporting ----

    private static String render(Options options, List<Result> results) {
        StringBuilder out = new StringBuilder("# Reply Quality: ").append(options.label()).append("\n\n");
        out.append(options.rescore().map(from -> "Saved replies of `" + from + "` scored again on "
                        + LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) + " with the current checks (no model call). Model: as in that run")
                .orElse("Run " + LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) + ". Model: " + options.llm().describe())).append(". Prompt versions seen: ").append(promptVersions(results))
                .append(options.composerPrompt().map(file -> ". Composer prompt variant: `" + file + "`").orElse(""))
                .append(". ").append(options.singleAgent().map(file -> "Single-agent baseline, prompt `" + file + "`")
                        .orElse((options.architecture() == PrototypeApp.Architecture.BLACKBOARD ? "Blackboard" : "Router")
                                + " architecture")).append(options.knowledge().equals(KNOWLEDGE) ? "" : ", knowledge `" + options.knowledge() + "`")
                .append(", rules-only metadata, threshold ")
                .append(org.supermas.control.AgendaStrategy.Settings.defaults().minContribution())
                .append(". Checks: frozen `reply-gold/v1` files, keyword-based (an approximation of a human rubric; ")
                .append("see `docs/benchmark/reply-quality.md`).\n\n");
        Map<String, List<Result>> groups = new LinkedHashMap<>();
        for (Result result : results) {
            String expected = result.item().gold().replyExpected() ? "reply expected" : "no reply expected";
            groups.computeIfAbsent(result.item().set() + " r" + result.repeat() + ", " + expected, key -> new ArrayList<>())
                    .add(result);
        }
        out.append("## Summary\n\n| Group | Emails | Drafted | Greeting right | Language right | Facts mentioned | "
                + "Asked for | Emails without violation | Latency p50 / p95 (s) |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        groups.forEach((group, rows) -> out.append("| ").append(group).append(" | ").append(rows.size())
                .append(" | ").append(count(rows, Result::drafted))
                .append(" | ").append(count(rows, r -> r.score().greetingOk()))
                .append(" | ").append(count(rows, r -> r.score().languageOk()))
                .append(" | ").append(ratio(rows, r -> r.score().mentioned().size(),
                        r -> r.score().mentioned().size() + r.score().notMentioned().size()))
                .append(" | ").append(ratio(rows, r -> r.score().asked().size(),
                        r -> r.score().asked().size() + r.score().notAsked().size()))
                .append(" | ").append(count(rows, r -> r.drafted() && r.score().clean()))
                .append(" | ").append(seconds(percentile(rows, 50))).append(" / ").append(seconds(percentile(rows, 95)))
                .append(" |\n"));
        out.append("\nOnly drafted emails can be without violation. \"Facts mentioned\" and \"Asked for\" are summed over "
                + "the expectations of the group.\n\n## Violations by kind\n\n| Kind | Count |\n| --- | --- |\n");
        Map<String, Integer> kinds = new TreeMap<>();
        results.stream().filter(Result::drafted).forEach(r -> r.score().violations().forEach(kind -> kinds.merge(kind, 1, Integer::sum)));
        kinds.forEach((kind, n) -> out.append("| ").append(kind).append(" | ").append(n).append(" |\n"));
        long guarded = results.stream().filter(r -> r.explanation().contains("removed the placeholder")).count();
        out.append("\nPlaceholders removed by the generation guard (not counted as violations): ").append(guarded).append(".\n");
        out.append("\n## Per email\n\n| Set | Repeat | Email | Status | Greeting | Missing facts | Not asked | Violations | Seconds |\n"
                + "| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (Result r : results) {
            out.append("| ").append(r.item().set()).append(" | ").append(r.repeat()).append(" | ").append(r.item().name())
                    .append(r.item().gold().replyExpected() ? "" : " (no reply expected)").append(" | ").append(r.status())
                    .append(" | ").append(r.score().greetingOk() ? "ok" : "wrong")
                    .append(" | ").append(String.join(", ", r.score().notMentioned()))
                    .append(" | ").append(String.join(", ", r.score().notAsked()))
                    .append(" | ").append(r.drafted() ? String.join(", ", r.score().violations()) : "no draft: " + r.failure())
                    .append(" | ").append(seconds(r.millis())).append(" |\n");
        }
        return out.toString();
    }

    private static String drafts(Options options, List<Result> results) {
        StringBuilder out = new StringBuilder("# Drafts: ").append(options.label()).append("\n\nModel: ")
                .append(options.llm().describe()).append(".\n");
        for (Result r : results) {
            out.append("\n## ").append(r.item().set()).append(" r").append(r.repeat()).append(" — ").append(r.item().name())
                    .append(": ").append(r.item().email().subject()).append("\n\n")
                    .append("*Expectation:* ").append(r.item().gold().note()).append("  \n")
                    .append("*Checks:* greeting ").append(r.score().greetingOk() ? "ok" : "wrong")
                    .append("; mentioned ").append(r.score().mentioned()).append(", missing ").append(r.score().notMentioned())
                    .append("; asked ").append(r.score().asked()).append(", not asked ").append(r.score().notAsked())
                    .append("; violations ").append(r.score().violations()).append("  \n")
                    .append("*Explanation:* ").append(r.explanation().isEmpty() ? r.failure() : r.explanation()).append("\n\n");
            if (r.body().isEmpty()) {
                out.append("(no draft)\n");
            } else {
                r.body().lines().forEach(line -> out.append("> ").append(line).append('\n'));
            }
        }
        return out.toString();
    }

    private static ObjectNode json(Result r) {
        ObjectNode node = JSON.createObjectNode();
        node.put("set", r.item().set());
        node.put("repeat", r.repeat());
        node.put("email", r.item().name());
        node.put("replyExpected", r.item().gold().replyExpected());
        node.put("status", r.status().name());
        node.put("failure", r.failure());
        node.put("millis", Math.round(r.millis()));
        node.put("greetingOk", r.score().greetingOk());
        node.put("languageOk", r.score().languageOk());
        node.set("mentioned", JSON.valueToTree(r.score().mentioned()));
        node.set("notMentioned", JSON.valueToTree(r.score().notMentioned()));
        node.set("asked", JSON.valueToTree(r.score().asked()));
        node.set("notAsked", JSON.valueToTree(r.score().notAsked()));
        node.set("violations", JSON.valueToTree(r.score().violations()));
        node.put("explanation", r.explanation());
        node.put("body", r.body());
        return node;
    }

    private static String promptVersions(List<Result> results) {
        List<String> versions = new ArrayList<>();
        for (Result r : results) {
            Matcher matcher = PROMPT.matcher(r.explanation());
            while (matcher.find()) {
                if (!versions.contains(matcher.group(1))) {
                    versions.add(matcher.group(1));
                }
            }
        }
        return versions.isEmpty() ? "none" : String.join(", ", versions);
    }

    private static String count(List<Result> rows, Predicate<Result> test) {
        return rows.stream().filter(test).count() + "";
    }

    private static String ratio(List<Result> rows, ToDoubleFunction<Result> hits, ToDoubleFunction<Result> total) {
        double sumTotal = rows.stream().mapToDouble(total).sum();
        if (sumTotal == 0) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.0f / %.0f", rows.stream().mapToDouble(hits).sum(), sumTotal);
    }

    private static double percentile(List<Result> rows, int p) {
        double[] values = rows.stream().mapToDouble(Result::millis).sorted().toArray();
        if (values.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * values.length) - 1;
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    private static String seconds(double millis) {
        return String.format(Locale.ROOT, "%.1f", millis / 1000);
    }
}
