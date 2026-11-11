package org.supermas.prototype;

import org.supermas.agent.role.Contribution;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.context.ProjectIndexes;
import org.supermas.control.AgendaStrategy;
import org.supermas.llm.LlmSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Benchmark plan C10: do the specialists' reply plans list what the reply must ask the sender for, and nothing
 * else? Runs the prototype (blackboard, template generation, rules) on the synthetic sets and compares the missing
 * items of the latest contributions, which the composer merges, with each email's gold {@code mustAsk} checks.
 * Field names are read as words (ID is "student number", COURSE is "course"). Writes counts and item labels only.
 *
 * <p>Usage: {@code MissingInformationBenchmark REPORT_FILE WORK_DIR}
 */
public final class MissingInformationBenchmark {
    private static final Path KNOWLEDGE = Path.of("src/test/resources/university/knowledge");
    private static final Map<String, String> FIELD_WORDS = Map.of("ID", "student number", "COURSE", "course",
            "DATE", "date", "DEADLINE", "deadline", "PERSON", "name");

    private MissingInformationBenchmark() { }

    record Row(String set, String email, boolean replyExpected, List<String> goldAsks, List<String> found,
               List<String> missed, List<String> listed) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: MissingInformationBenchmark REPORT_FILE WORK_DIR");
        }
        List<Row> rows = new ArrayList<>();
        rows.addAll(run("dev", Path.of("benchmark/data/synthetic-dev"), Path.of(args[1]).resolve("dev")));
        rows.addAll(run("multi", Path.of("benchmark/data/synthetic-multi"), Path.of(args[1]).resolve("multi")));
        Files.writeString(Path.of(args[0]), render(rows));
        System.out.print(render(rows));
    }

    static List<Row> run(String set, Path folder, Path data) throws Exception {
        Files.createDirectories(data);
        List<Row> rows = new ArrayList<>();
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(data.resolve("index"))) {
            PrototypeApp.Wiring wiring = PrototypeApp.Wiring.create(indexes, new PrototypeApp.Options(null, KNOWLEDGE, data,
                    "uni", PrototypeApp.Architecture.BLACKBOARD, AgendaStrategy.Settings.defaults().minContribution(),
                    LlmSettings.template()));
            for (ReplyQualityBenchmark.Item item : ReplyQualityBenchmark.load(set, folder)) {
                BlackboardSnapshot state = wiring.coordinator.run(wiring.coordinator.receive(item.email()),
                        PrototypeApp.Architecture.BLACKBOARD.limits());
                Set<String> listed = new LinkedHashSet<>();
                for (Contribution contribution : Contribution.latest(state.evidence())) {
                    listed.addAll(contribution.plan().missing());
                }
                String asWords = ReplyChecks.normalize(String.join(" | ", listed.stream()
                        .map(item2 -> FIELD_WORDS.getOrDefault(item2, item2)).toList()));
                List<String> gold = new ArrayList<>();
                List<String> found = new ArrayList<>();
                List<String> missed = new ArrayList<>();
                for (ReplyChecks.Term ask : item.gold().mustAsk()) {
                    gold.add(ask.id());
                    boolean hit = ask.any().stream().map(ReplyChecks::normalize).anyMatch(asWords::contains);
                    (hit ? found : missed).add(ask.id());
                }
                rows.add(new Row(set, item.name(), item.gold().replyExpected(), gold, found, missed, new ArrayList<>(listed)));
            }
        }
        return rows;
    }

    static String render(List<Row> rows) {
        List<Row> expected = rows.stream().filter(Row::replyExpected).toList();
        int gold = expected.stream().mapToInt(r -> r.goldAsks().size()).sum();
        int found = expected.stream().mapToInt(r -> r.found().size()).sum();
        long withGold = expected.stream().filter(r -> !r.goldAsks().isEmpty()).count();
        long overAsk = expected.stream().filter(r -> r.goldAsks().isEmpty() && !r.listed().isEmpty()).count();
        long noReplyListing = rows.stream().filter(r -> !r.replyExpected() && !r.listed().isEmpty()).count();
        StringBuilder out = new StringBuilder("# Missing Information (benchmark plan C10)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z. Prototype on the synthetic sets: blackboard, ")
                .append("template generation, rules-only metadata and missing-information rules plus knowledge requirements. ")
                .append("Listed items are the missing items of the latest contributions (what the composer merges), compared ")
                .append("with the frozen `mustAsk` checks (`reply-gold/v1`, not yet reviewed by a person).\n\n")
                .append("| Measure | Value |\n| --- | --- |\n")
                .append("| Reply-expected emails | ").append(expected.size()).append(" |\n")
                .append("| Gold asks found in the plans (recall) | ").append(found).append(" / ").append(gold)
                .append(String.format(Locale.ROOT, " (%.2f)", gold == 0 ? 0.0 : (double) found / gold)).append(" |\n")
                .append("| Emails with a gold ask | ").append(withGold).append(" |\n")
                .append("| Emails with no gold ask but a listed item (over-asking) | ").append(overAsk).append(" of ")
                .append(expected.size() - withGold).append(" |\n")
                .append("| No-reply emails with a listed item | ").append(noReplyListing).append(" of ")
                .append(rows.size() - expected.size()).append(" |\n\n")
                .append("## Per email (reply expected, or with a listed item)\n\n")
                .append("| Set | Email | Gold asks | Found | Missed | Listed by the plans |\n| --- | --- | --- | --- | --- | --- |\n");
        for (Row row : rows) {
            if (row.replyExpected() || !row.listed().isEmpty()) {
                out.append("| ").append(row.set()).append(" | ").append(row.email()).append(row.replyExpected() ? "" : " (no reply)")
                        .append(" | ").append(String.join(", ", row.goldAsks())).append(" | ").append(String.join(", ", row.found()))
                        .append(" | ").append(String.join(", ", row.missed())).append(" | ")
                        .append(String.join("; ", row.listed()).replace("|", "/")).append(" |\n");
            }
        }
        return out.toString();
    }
}
