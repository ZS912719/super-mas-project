package org.supermas.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.supermas.metadata.MetadataAgent;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;

/**
 * Does the reply-need classifier ({@link org.supermas.metadata.ReplyNeedClassifier}, via the metadata agent) agree
 * with a person on whether a reply is needed? Plan {@code docs/plans/reply-needed.md}.
 *
 * <ul>
 *   <li>{@code synthetic OWNERS REPORT DIR...}: gold is the frozen reply checks' {@code replyExpected} (development);
 *   <li>{@code enron OWNERS REPORT EMAIL_DIR LABEL_DIR RATER}: gold is a person's label (yes / no / unsure; unsure is
 *       left out of the matrix and counted separately) from the labelling page (test).
 * </ul>
 * OWNERS is a comma-separated list of owner addresses, or {@code -} for none. The positive class is "no reply needed",
 * the decision that suppresses a draft; its precision is the key number. 95 % intervals by bootstrap over emails
 * (10,000 resamples, seed 20261111). The report holds email names, verdicts and the rule that decided, no email text.
 */
public final class ReplyNeedBenchmark {
    private ReplyNeedBenchmark() { }

    record Row(String name, String gold, String verdict, String rule) { }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Set<String> owners = args[1].equals("-") ? Set.of() : Arrays.stream(args[1].split(",")).map(String::strip)
                .collect(Collectors.toSet());
        MetadataAgent metadata = MetadataAgent.rulesOnly().withOwners(owners);
        MimeEmailParser parser = new MimeEmailParser();
        ObjectMapper json = new ObjectMapper();
        List<Row> rows = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        if (mode.equals("synthetic")) {
            for (int i = 3; i < args.length; i++) dirs.add(Path.of(args[i]));
        } else {
            dirs.add(Path.of(args[3]));
        }
        for (Path dir : dirs) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".eml")).sorted().toList()) {
                    String name = file.getFileName().toString().replace(".eml", "");
                    String gold;
                    if (mode.equals("synthetic")) {
                        gold = json.readTree(file.resolveSibling(name + ".reply.json").toFile()).path("replyExpected").asBoolean()
                                ? "needed" : "not needed";
                    } else {
                        Path label = Path.of(args[4], name + "." + args[5] + ".json");
                        if (!Files.exists(label)) {
                            continue;
                        }
                        String value = json.readTree(label.toFile()).path("replyNeeded").asText();
                        gold = switch (value) { case "yes" -> "needed"; case "no" -> "not needed"; default -> "unsure"; };
                    }
                    Email email = parser.parse(Files.readAllBytes(file), "uni");
                    Analysis analysis = metadata.analyze(email);
                    String verdict = analysis.metadata().get(MetadataAgent.KEY_REPLY_NEED);
                    JsonNode reasons = json.readTree(analysis.metadata().get(MetadataAgent.KEY_REPLY_NEED_REASONS));
                    rows.add(new Row(name, gold, verdict, rule(reasons.size() == 0 ? "" : reasons.get(0).asText())));
                }
            }
        }
        String report = report(mode, args, rows);
        Files.writeString(Path.of(args[2]), report);
        System.out.print(report);
    }

    /** The rule that decided, without addresses or text from the email. */
    static String rule(String reason) {
        if (reason.startsWith("automated sender")) return "automated sender";
        if (reason.startsWith("mailing-list header") || reason.startsWith("bulk mail") || reason.startsWith("automatically submitted")) return "list or bulk header";
        if (reason.startsWith("broadcast (")) return "broadcast with a question";
        if (reason.startsWith("broadcast")) return "broadcast";
        return reason;
    }

    private static String report(String mode, String[] args, List<Row> rows) {
        List<Row> judged = rows.stream().filter(r -> !r.gold().equals("unsure")).toList();
        int tp = count(judged, "not needed", true), fp = count(judged, "needed", true);
        int fn = count(judged, "not needed", false), tn = count(judged, "needed", false);
        StringBuilder out = new StringBuilder("# Is a reply needed? (plan reply-needed)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z, mode `").append(mode).append("`, owners `")
                .append(args[1]).append("`. Positive class: no reply needed (the decision that suppresses a draft); `UNSURE` ")
                .append("counts as reply needed.\n\n")
                .append("| | Gold: no reply needed | Gold: reply needed |\n| --- | --- | --- |\n")
                .append("| System: no reply needed | ").append(tp).append(" | **").append(fp).append("** |\n")
                .append("| System: reply needed or unsure | ").append(fn).append(" | ").append(tn).append(" |\n\n");
        out.append("Precision of \"no reply needed\": ").append(ratio(tp, tp + fp)).append(interval(judged, true))
                .append("; recall: ").append(ratio(tp, tp + fn)).append(interval(judged, false))
                .append("; accuracy: ").append(ratio(tp + tn, judged.size())).append(" of ").append(judged.size()).append(" emails.");
        long unsure = rows.size() - judged.size();
        if (unsure > 0) {
            out.append(" Gold \"unsure\": ").append(unsure).append(" (left out; system said not needed for ")
                    .append(rows.stream().filter(r -> r.gold().equals("unsure") && r.verdict().equals("NOT_NEEDED")).count()).append(").");
        }
        out.append("\n\n## Rule that decided\n\n| Rule | Emails | Gold: no reply needed | Gold: reply needed | Gold: unsure |\n| --- | --- | --- | --- | --- |\n");
        Map<String, List<Row>> byRule = new LinkedHashMap<>();
        rows.forEach(r -> byRule.computeIfAbsent(r.verdict() + ": " + r.rule(), k -> new ArrayList<>()).add(r));
        byRule.forEach((rule, list) -> out.append("| ").append(rule).append(" | ").append(list.size()).append(" | ")
                .append(list.stream().filter(r -> r.gold().equals("not needed")).count()).append(" | ")
                .append(list.stream().filter(r -> r.gold().equals("needed")).count()).append(" | ")
                .append(list.stream().filter(r -> r.gold().equals("unsure")).count()).append(" |\n"));
        out.append("\n## Per email\n\n| Email | Gold | System | Rule |\n| --- | --- | --- | --- |\n");
        rows.forEach(r -> out.append("| ").append(r.name()).append(" | ").append(r.gold()).append(" | ").append(r.verdict())
                .append(" | ").append(r.rule()).append(" |\n"));
        return out.toString();
    }

    private static int count(List<Row> rows, String gold, boolean systemNotNeeded) {
        return (int) rows.stream().filter(r -> r.gold().equals(gold) && r.verdict().equals("NOT_NEEDED") == systemNotNeeded).count();
    }

    private static String ratio(int a, int b) {
        return b == 0 ? "–" : a + " / " + b + String.format(java.util.Locale.ROOT, " (%.2f)", a / (double) b);
    }

    /** Bootstrap over emails of precision (true) or recall (false) of "no reply needed". */
    private static String interval(List<Row> rows, boolean precision) {
        if (rows.isEmpty()) return "";
        Random random = new Random(20261111L);
        double[] values = new double[10_000];
        int n = 0;
        for (int b = 0; b < values.length; b++) {
            int hit = 0, total = 0;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(random.nextInt(rows.size()));
                boolean system = r.verdict().equals("NOT_NEEDED"), gold = r.gold().equals("not needed");
                if (precision ? system : gold) {
                    total++;
                    hit += system && gold ? 1 : 0;
                }
            }
            if (total > 0) values[n++] = hit / (double) total;
        }
        if (n == 0) return "";
        double[] sorted = Arrays.copyOf(values, n);
        Arrays.sort(sorted);
        return String.format(java.util.Locale.ROOT, " [95 %% CI %.2f–%.2f]", sorted[(int) (0.025 * n)], sorted[(int) (0.975 * n) - 1]);
    }
}
