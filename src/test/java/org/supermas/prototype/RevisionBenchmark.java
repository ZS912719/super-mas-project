package org.supermas.prototype;

import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.context.ProjectIndexes;
import org.supermas.control.AgendaStrategy;
import org.supermas.llm.LlmSettings;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.Draft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Benchmark plan P7: after a rejection with feedback, does the next version apply the feedback without breaking
 * what was right? For every reply-expected synthetic email: draft v1, reject it with one feedback chosen from the
 * frozen checks (the first gold fact v1 misses: "Please also mention: ..."; else the first gold ask it misses:
 * "Please ask the sender for ..."; else "Please make the reply shorter."), run the coordinator again and score v2.
 * Writes check identifiers and counts only, no email or reply text.
 *
 * <p>Usage: {@code RevisionBenchmark REPORT_FILE WORK_DIR [--llm-... VALUE]} (model settings as for the prototype;
 * with the template engine it only checks the wiring, because the template ignores feedback).
 */
public final class RevisionBenchmark {
    private static final Path KNOWLEDGE = Path.of("src/test/resources/university/knowledge");

    private RevisionBenchmark() { }

    enum Kind { FACT, ASK, SHORTER }

    record Row(String set, String email, Kind kind, String target, boolean v1Drafted, boolean v2Drafted, boolean applied,
               List<String> factsLost, List<String> newViolations, int v1Length, int v2Length, String failure) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: RevisionBenchmark REPORT_FILE WORK_DIR [--llm-... VALUE]");
        }
        Map<String, String> flags = new HashMap<>();
        for (int i = 2; i + 1 < args.length; i += 2) {
            if (!LlmSettings.isFlag(args[i])) {
                throw new IllegalArgumentException("Unknown option " + args[i]);
            }
            flags.put(args[i], args[i + 1]);
        }
        LlmSettings llm = LlmSettings.from(System.getenv(), flags);
        Path work = Path.of(args[1]);
        List<Row> rows = new ArrayList<>();
        rows.addAll(run("dev", Path.of("benchmark/data/synthetic-dev"), work.resolve("dev"), llm));
        rows.addAll(run("multi", Path.of("benchmark/data/synthetic-multi"), work.resolve("multi"), llm));
        String report = render(rows, llm);
        Files.writeString(Path.of(args[0]), report);
        System.out.print(report);
    }

    static List<Row> run(String set, Path folder, Path data, LlmSettings settings) throws Exception {
        Files.createDirectories(data);
        LlmSettings llm = settings.provider() == LlmSettings.Provider.TEMPLATE ? settings
                : settings.withCache(settings.cache().orElse(data.resolve("llm-cache")));
        List<Row> rows = new ArrayList<>();
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(data.resolve("index"))) {
            PrototypeApp.Wiring wiring = PrototypeApp.Wiring.create(indexes, new PrototypeApp.Options(null, KNOWLEDGE, data,
                    "uni", PrototypeApp.Architecture.BLACKBOARD, AgendaStrategy.Settings.defaults().minContribution(), llm));
            BoardReviewService review = new BoardReviewService(new DefaultBlackboardFactory(wiring.store), Clock.systemUTC());
            for (ReplyQualityBenchmark.Item item : ReplyQualityBenchmark.load(set, folder)) {
                if (!item.gold().replyExpected()) {
                    continue;
                }
                BlackboardSnapshot first = wiring.coordinator.run(wiring.coordinator.receive(item.email()),
                        PrototypeApp.BLACKBOARD_LIMITS);
                Draft v1 = last(first);
                if (first.status() != BoardStatus.AWAITING_REVIEW || v1 == null) {
                    rows.add(new Row(set, item.name(), null, "", false, false, false, List.of(), List.of(), 0, 0,
                            first.failure().map(f -> f.kind().toString()).orElse(first.status().toString())));
                    continue;
                }
                ReplyChecks.Score s1 = ReplyChecks.score(item.gold(), v1.body());
                Kind kind;
                String target;
                String feedback;
                ReplyChecks.Term missingFact = item.gold().mustMention().stream()
                        .filter(t -> s1.notMentioned().contains(t.id())).findFirst().orElse(null);
                ReplyChecks.Term missingAsk = item.gold().mustAsk().stream()
                        .filter(t -> s1.notAsked().contains(t.id())).findFirst().orElse(null);
                if (missingFact != null) {
                    kind = Kind.FACT;
                    target = missingFact.id();
                    feedback = "Please also mention: " + missingFact.any().get(0) + ".";
                } else if (missingAsk != null) {
                    kind = Kind.ASK;
                    target = missingAsk.id();
                    feedback = "Please ask the sender for the " + missingAsk.any().get(0) + ".";
                } else {
                    kind = Kind.SHORTER;
                    target = "length";
                    feedback = "Please make the reply shorter.";
                }
                if ("fr".equals(item.gold().language())) {
                    feedback = switch (kind) {
                        case FACT -> "Merci de mentionner aussi : " + missingFact.any().get(0) + ".";
                        case ASK -> "Merci de demander à l'expéditeur : " + missingAsk.any().get(0) + ".";
                        case SHORTER -> "Merci de raccourcir la réponse.";
                    };
                }
                review.reject(first.id(), v1.version(), feedback);
                BlackboardSnapshot second = wiring.coordinator.run(first.id(), PrototypeApp.BLACKBOARD_LIMITS);
                Draft v2 = second.drafts().size() > first.drafts().size() ? last(second) : null;
                if (second.status() != BoardStatus.AWAITING_REVIEW || v2 == null) {
                    rows.add(new Row(set, item.name(), kind, target, true, false, false, List.of(), List.of(),
                            v1.body().length(), 0, second.failure().map(f -> f.kind() + ": " + f.message()).orElse(second.status().toString())));
                    continue;
                }
                ReplyChecks.Score s2 = ReplyChecks.score(item.gold(), v2.body());
                boolean applied = switch (kind) {
                    case FACT -> s2.mentioned().contains(target);
                    case ASK -> s2.asked().contains(target);
                    case SHORTER -> v2.body().length() < v1.body().length();
                };
                List<String> lost = s1.mentioned().stream().filter(id -> !s2.mentioned().contains(id)).toList();
                List<String> added = s2.violations().stream().filter(v -> !s1.violations().contains(v)).toList();
                rows.add(new Row(set, item.name(), kind, target, true, true, applied, lost, added, v1.body().length(),
                        v2.body().length(), ""));
                System.out.println("  " + item.name() + ": " + kind + " " + (applied ? "applied" : "not applied"));
            }
        }
        return rows;
    }

    private static Draft last(BlackboardSnapshot state) {
        return state.drafts().isEmpty() ? null : state.drafts().get(state.drafts().size() - 1);
    }

    static String render(List<Row> rows, LlmSettings llm) {
        List<Row> revised = rows.stream().filter(Row::v2Drafted).toList();
        StringBuilder out = new StringBuilder("# Revision After Feedback (benchmark plan P7)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z. Model: ").append(llm.describe())
                .append(". Blackboard architecture on the reply-expected synthetic emails; feedback chosen from the frozen ")
                .append("reply checks (first missing fact, else first missing ask, else \"shorter\"), in the email's language.\n\n")
                .append("| Feedback kind | Emails | v2 drafted | Feedback applied | Facts lost | New violations |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        for (Kind kind : Kind.values()) {
            List<Row> of = rows.stream().filter(r -> r.kind() == kind).toList();
            long drafted = of.stream().filter(Row::v2Drafted).count();
            long applied = of.stream().filter(Row::applied).count();
            long lost = of.stream().mapToLong(r -> r.factsLost().size()).sum();
            long violations = of.stream().mapToLong(r -> r.newViolations().size()).sum();
            out.append("| ").append(kind.toString().toLowerCase(Locale.ROOT)).append(" | ").append(of.size()).append(" | ")
                    .append(drafted).append(" | ").append(applied).append(" / ").append(drafted).append(" | ").append(lost)
                    .append(" | ").append(violations).append(" |\n");
        }
        long noFirst = rows.stream().filter(r -> !r.v1Drafted()).count();
        out.append("\nEmails without a first draft: ").append(noFirst).append(". Revised: ").append(revised.size())
                .append(" of ").append(rows.size()).append(".\n\n| Set | Email | Kind | Target | Applied | Facts lost | New violations | Length v1 → v2 | Failure |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (Row r : rows) {
            out.append("| ").append(r.set()).append(" | ").append(r.email()).append(" | ")
                    .append(r.kind() == null ? "—" : r.kind().toString().toLowerCase(Locale.ROOT)).append(" | ").append(r.target())
                    .append(" | ").append(r.v2Drafted() ? (r.applied() ? "yes" : "no") : "—").append(" | ")
                    .append(String.join(", ", r.factsLost())).append(" | ").append(String.join(", ", r.newViolations()))
                    .append(" | ").append(r.v1Length()).append(" → ").append(r.v2Length()).append(" | ")
                    .append(r.failure().replace("|", "/")).append(" |\n");
        }
        return out.toString();
    }
}
