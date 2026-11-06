package org.supermas.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.supermas.agent.collaboration.RequestEntry;
import org.supermas.agent.role.Contribution;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.context.ProjectIndexes;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.Email;

/**
 * Step 12.7: the step 11 router against the Phase 12 blackboard, on the 24 single-intent synthetic emails and on
 * the 12 multi-request ones, through the prototype's real wiring (SQLite board, template generation, rules-only
 * metadata). Each set runs on a fresh data folder per architecture, so no email sees another one's history.
 *
 * <p>Usage: {@code BlackboardBenchmark DEV_DIR MULTI_DIR REPORT_FILE WORK_DIR}. It lives in the prototype package
 * because it uses the package-private wiring.
 */
public final class BlackboardBenchmark {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double DEFAULT_MIN = org.supermas.control.AgendaStrategy.Settings.defaults().minContribution();
    private static final double[] SWEEP = {0.1, 0.2, 0.3, 0.4, 0.5};

    private BlackboardBenchmark() { }

    record Item(String name, Email email, Set<String> gold, int goldRequests) { }

    record Result(Item item, BoardStatus status, String author, Set<String> contributors, int requests,
                  boolean allCovered, int rounds, long revisions, double millis) { }

    public static void main(String[] args) throws Exception {
        Path dev = Path.of(args[0]);
        Path multi = Path.of(args[1]);
        Path report = Path.of(args[2]);
        Path work = Path.of(args[3]);
        List<Item> devItems = load(dev, false);
        List<Item> multiItems = load(multi, true);
        Map<String, List<Result>> results = new LinkedHashMap<>();
        for (PrototypeApp.Architecture architecture : PrototypeApp.Architecture.values()) {
            results.put("dev-" + architecture, run(devItems, architecture, DEFAULT_MIN, work.resolve("dev-" + architecture)));
            results.put("multi-" + architecture, run(multiItems, architecture, DEFAULT_MIN, work.resolve("multi-" + architecture)));
        }
        // Sensitivity of the one blackboard threshold. Reported, not used to pick the default after the fact.
        for (double min : SWEEP) {
            results.put("dev-sweep-" + min, run(devItems, PrototypeApp.Architecture.BLACKBOARD, min, work.resolve("dev-" + min)));
            results.put("multi-sweep-" + min, run(multiItems, PrototypeApp.Architecture.BLACKBOARD, min, work.resolve("multi-" + min)));
        }
        Files.createDirectories(report.toAbsolutePath().getParent());
        Files.writeString(report, render(results, devItems.size(), multiItems.size()));
        System.out.println("Report written to " + report);
    }

    private static List<Item> load(Path folder, boolean multi) throws Exception {
        List<Path> files;
        try (Stream<Path> listing = Files.list(folder)) {
            files = listing.filter(path -> path.toString().endsWith(".eml")).sorted().toList();
        }
        MimeEmailParser parser = new MimeEmailParser();
        List<Item> items = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".eml", "");
            JsonNode gold = JSON.readTree(file.resolveSibling(name + ".gold.json").toFile());
            Email email = parser.parse(Files.readAllBytes(file), "uni");
            Set<String> roles = new TreeSet<>();
            int requests = 0;
            if (multi) {
                gold.path("contributors").forEach(role -> roles.add(role.asText()));
                requests = gold.path("requests").asInt();
            } else {
                String hint = gold.path("roleHint").isNull() ? null : gold.path("roleHint").asText();
                roles.add(hint == null ? "generic" : hint.equals("researcher") ? "research" : hint);
            }
            items.add(new Item(name, email, roles, requests));
        }
        return items;
    }

    private static List<Result> run(List<Item> items, PrototypeApp.Architecture architecture, double min, Path data)
            throws Exception {
        Files.createDirectories(data);
        List<Result> results = new ArrayList<>();
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(data.resolve("index"))) {
            PrototypeApp.Wiring wiring = PrototypeApp.Wiring.create(indexes, new PrototypeApp.Options(null,
                    Path.of("src/test/resources/university/knowledge"), data, "uni", architecture, min));
            DefaultBlackboardFactory boards = new DefaultBlackboardFactory(wiring.store);
            for (Item item : items) {
                long start = System.nanoTime();
                BoardId board = wiring.coordinator.receive(item.email());
                BlackboardSnapshot state = wiring.coordinator.run(board, architecture.limits());
                double millis = (System.nanoTime() - start) / 1e6;
                String author = state.drafts().isEmpty() ? "-" : state.drafts().get(state.drafts().size() - 1).agentId();
                Set<String> contributors = new TreeSet<>();
                List<Contribution> contributions = Contribution.latest(state.evidence());
                contributions.forEach(found -> contributors.add(found.agentId()));
                if (architecture == PrototypeApp.Architecture.ROUTER && !author.equals("-")) {
                    contributors.add(author);
                }
                List<RequestEntry> requests = RequestEntry.all(state.evidence());
                Set<Integer> covered = new TreeSet<>();
                contributions.forEach(found -> covered.addAll(found.covers()));
                boolean allCovered = !requests.isEmpty() && requests.stream().allMatch(entry -> covered.contains(entry.index()));
                results.add(new Result(item, state.status(), author, contributors, requests.size(), allCovered,
                        state.budgetUsage().rounds(), boards.open(board).versionedSnapshot().revision(), millis));
            }
        }
        return results;
    }

    private static String render(Map<String, List<Result>> all, int devSize, int multiSize) {
        StringBuilder out = new StringBuilder("# Router Against Blackboard\n\n");
        out.append("Step 12.7, run on ").append(java.time.LocalDate.now()).append(". Rules-only metadata, template ")
                .append("generation (no language model), SQLite board, fresh data per set and architecture. See ")
                .append("`docs/reports/step-12.7.md`.\n\n");
        out.append("## Single-intent emails (`synthetic-dev`, ").append(devSize).append(", gold role from the labelled role hint)\n\n");
        out.append("| Measure | Router | Blackboard |\n| --- | --- | --- |\n");
        List<Result> devRouter = all.get("dev-ROUTER");
        List<Result> devBoard = all.get("dev-BLACKBOARD");
        out.append(row("Answering agents equal the gold role", count(devRouter, r -> r.contributors().equals(r.item().gold())),
                count(devBoard, r -> r.contributors().equals(r.item().gold())), devSize));
        out.append(row("Gold role among the answering agents", count(devRouter, r -> r.contributors().containsAll(r.item().gold())),
                count(devBoard, r -> r.contributors().containsAll(r.item().gold())), devSize));
        out.append(row("Drafts awaiting review", count(devRouter, r -> r.status() == BoardStatus.AWAITING_REVIEW),
                count(devBoard, r -> r.status() == BoardStatus.AWAITING_REVIEW), devSize));
        out.append("| Mean answering agents per email | ").append(format(mean(devRouter, r -> r.contributors().size())))
                .append(" | ").append(format(mean(devBoard, r -> r.contributors().size()))).append(" |\n");
        cost(out, devRouter, devBoard);

        out.append("\n## Multi-request emails (`synthetic-multi`, ").append(multiSize).append(")\n\n");
        List<Result> multiRouter = all.get("multi-ROUTER");
        List<Result> multiBoard = all.get("multi-BLACKBOARD");
        out.append("| Measure | Router | Blackboard |\n| --- | --- | --- |\n");
        out.append(row("Answering agents equal the gold set", count(multiRouter, r -> r.contributors().equals(r.item().gold())),
                count(multiBoard, r -> r.contributors().equals(r.item().gold())), multiSize));
        double[] router = precisionRecall(multiRouter);
        double[] board = precisionRecall(multiBoard);
        out.append("| Role precision (micro) | ").append(format(router[0])).append(" | ").append(format(board[0])).append(" |\n");
        out.append("| Role recall (micro) | ").append(format(router[1])).append(" | ").append(format(board[1])).append(" |\n");
        out.append(row("Request count equals the gold count", 0, count(multiBoard, r -> r.requests() == r.item().goldRequests()),
                multiSize).replace("| 0 of", "| n/a (no request level), 0 of"));
        out.append(row("Every request answered by a contribution", 0, count(multiBoard, Result::allCovered), multiSize)
                .replace("| 0 of", "| n/a, 0 of"));
        cost(out, multiRouter, multiBoard);

        out.append("\n## Sensitivity of the contribution threshold (blackboard)\n\n")
                .append("The default (").append(DEFAULT_MIN).append(") was fixed before these sets existed. Lower ")
                .append("thresholds let weaker specialists join.\n\n")
                .append("| Threshold | Single-intent exact | Multi exact | Multi precision | Multi recall | Multi mean rounds |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        for (double min : SWEEP) {
            List<Result> d = all.get("dev-sweep-" + min);
            List<Result> m = all.get("multi-sweep-" + min);
            double[] pr = precisionRecall(m);
            out.append("| ").append(min).append(" | ").append(count(d, r -> r.contributors().equals(r.item().gold())))
                    .append(" of ").append(devSize).append(" | ").append(count(m, r -> r.contributors().equals(r.item().gold())))
                    .append(" of ").append(multiSize).append(" | ").append(format(pr[0])).append(" | ").append(format(pr[1]))
                    .append(" | ").append(format(mean(m, Result::rounds))).append(" |\n");
        }
        out.append("\n### Per email (multi-request)\n\n| Email | Gold | Router | Blackboard | Requests (gold) | Rounds R / B |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        for (int i = 0; i < multiBoard.size(); i++) {
            Result r = multiRouter.get(i);
            Result b = multiBoard.get(i);
            out.append("| ").append(b.item().name()).append(" | ").append(b.item().gold()).append(" | ").append(r.contributors())
                    .append(" | ").append(b.contributors()).append(" | ").append(b.requests()).append(" (")
                    .append(b.item().goldRequests()).append(") | ").append(r.rounds()).append(" / ").append(b.rounds()).append(" |\n");
        }
        out.append("\n### Per email (single-intent, mismatches only)\n\n| Email | Gold | Router | Blackboard |\n| --- | --- | --- | --- |\n");
        for (int i = 0; i < devBoard.size(); i++) {
            Result r = devRouter.get(i);
            Result b = devBoard.get(i);
            if (!r.contributors().equals(r.item().gold()) || !b.contributors().equals(b.item().gold())) {
                out.append("| ").append(b.item().name()).append(" | ").append(b.item().gold()).append(" | ")
                        .append(r.contributors()).append(" | ").append(b.contributors()).append(" |\n");
            }
        }
        return out.toString();
    }

    private static void cost(StringBuilder out, List<Result> router, List<Result> board) {
        out.append("| Mean rounds per email | ").append(format(mean(router, Result::rounds))).append(" | ")
                .append(format(mean(board, Result::rounds))).append(" |\n");
        out.append("| Mean SQLite revisions per email | ").append(format(mean(router, r -> r.revisions()))).append(" | ")
                .append(format(mean(board, r -> r.revisions()))).append(" |\n");
        out.append("| Median time per email (ms) | ").append(format(median(router))).append(" | ")
                .append(format(median(board))).append(" |\n");
    }

    private static double[] precisionRecall(List<Result> results) {
        int truePositive = 0;
        int predicted = 0;
        int gold = 0;
        for (Result result : results) {
            Set<String> hit = new TreeSet<>(result.contributors());
            hit.retainAll(result.item().gold());
            truePositive += hit.size();
            predicted += result.contributors().size();
            gold += result.item().gold().size();
        }
        return new double[] {predicted == 0 ? 0 : (double) truePositive / predicted, gold == 0 ? 0 : (double) truePositive / gold};
    }

    private static String row(String label, int router, int board, int total) {
        return "| " + label + " | " + router + " of " + total + " | " + board + " of " + total + " |\n";
    }

    private static int count(List<Result> results, java.util.function.Predicate<Result> test) {
        return (int) results.stream().filter(test).count();
    }

    private static double mean(List<Result> results, java.util.function.ToDoubleFunction<Result> value) {
        return results.stream().mapToDouble(value).average().orElse(0);
    }

    private static double median(List<Result> results) {
        double[] sorted = results.stream().mapToDouble(Result::millis).sorted().toArray();
        return sorted.length == 0 ? 0 : sorted[sorted.length / 2];
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
