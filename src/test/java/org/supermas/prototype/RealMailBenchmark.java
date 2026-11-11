package org.supermas.prototype;

import org.supermas.agent.role.Contribution;
import org.supermas.context.ProjectIndexes;
import org.supermas.control.AgendaStrategy;
import org.supermas.llm.LlmSettings;
import org.supermas.metadata.LanguageDetector;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.metadata.QuoteSignatureStripper;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.Email;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Benchmark plan W9: replies on real mail, where no gold answer exists. The system (blackboard, the configured model,
 * the university knowledge it is built for) answers the 100 emails of the frozen Enron test set, and every reply is
 * checked for what can be judged without knowing the right answer:
 * <ul>
 *   <li>drafted or failed, and latency;</li>
 *   <li>the greeting names the sender (display name or address) and never only a recipient;</li>
 *   <li>the frozen reply checks with no facts to mention: placeholder, signature, staff-only text, language;</li>
 *   <li>promises and claimed actions from a fixed list ({@link #PROMISES}, fixed before the first run);</li>
 *   <li>content of another email: a 6-word sequence shared with another test email and absent from this one;</li>
 *   <li>university knowledge imported into company mail ({@link #UNIVERSITY}, fixed before the first run);</li>
 *   <li>which agents answered.</li>
 * </ul>
 * Email and reply text go only to {@code OUT_DIR} (Git-ignored {@code runtime/}): {@code drafts.md} and a rubric
 * sample of 20 for a person. The report file holds counts and email identifiers only.
 *
 * <p>Usage: {@code RealMailBenchmark DATA_DIR REPORT_FILE OUT_DIR [--llm-... VALUE]}
 */
public final class RealMailBenchmark {
    private static final Path KNOWLEDGE = Path.of("src/test/resources/university/knowledge");
    /** Promises, decisions and claimed actions a draft must not contain (common.txt). Fixed before the first run. */
    static final List<String> PROMISES = List.of("i approve", "is approved", "has been approved", "i will pay", "has been paid",
            "i have paid", "i confirm", "is confirmed", "i have signed", "i will sign", "i agree to", "deal is done",
            "i have sent", "i've sent", "has been sent", "i have forwarded", "i've forwarded", "please find attached",
            "attached is", "i have attached", "i've attached", "i have scheduled", "i've scheduled", "i have booked",
            "i will be there", "see you on", "i have transferred", "i've transferred");
    /** Phrases of the university knowledge base that do not belong in a reply to company mail. Fixed before the first run. */
    static final List<String> UNIVERSITY = List.of("course leader", "medical certificate", "registrar", "seven days", "resit",
            "student number", "marking", "facilities portal", "research office", "enrolment", "assignment", "thesis",
            "faculty", "semester");
    private static final Set<String> NOT_NAMES = Set.of("enron", "com", "ect", "hou", "corp", "mail", "the", "and", "inc",
            "net", "org", "edu", "www", "ees", "ena", "na", "cc", "to", "from", "sent");
    private static final Pattern WORD = Pattern.compile("[\\p{L}]{3,}");
    private static final int SHARED_WORDS = 6;

    private RealMailBenchmark() { }

    record Row(String id, BoardStatus status, String failure, double millis, String contributors, boolean greetsSender,
               boolean greetsRecipientOnly, List<String> violations, List<String> promises, List<String> university,
               boolean otherEmailContent, int length) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: RealMailBenchmark DATA_DIR REPORT_FILE OUT_DIR [--llm-... VALUE]");
        }
        Map<String, String> flags = new HashMap<>();
        for (int i = 3; i + 1 < args.length; i += 2) {
            if (!LlmSettings.isFlag(args[i])) {
                throw new IllegalArgumentException("Unknown option " + args[i]);
            }
            flags.put(args[i], args[i + 1]);
        }
        Path out = Path.of(args[2]);
        if (Files.exists(out)) {
            throw new IllegalArgumentException(out + " exists; choose a new OUT_DIR so runs are never mixed");
        }
        Files.createDirectories(out);
        LlmSettings llm = LlmSettings.from(System.getenv(), flags);
        if (llm.provider() != LlmSettings.Provider.TEMPLATE) {
            llm = llm.withCache(llm.cache().orElse(out.resolve("llm-cache")));
        }
        List<Row> rows = run(Path.of(args[0]), out, llm);
        String report = render(rows, llm, Path.of(args[0]));
        Files.writeString(Path.of(args[1]), report);
        Files.writeString(out.resolve("results.md"), report);
        System.out.print(report);
    }

    static List<Row> run(Path dataDir, Path out, LlmSettings llm) throws Exception {
        List<Path> files;
        try (Stream<Path> listing = Files.list(dataDir)) {
            files = listing.filter(p -> p.toString().endsWith(".eml")).sorted().toList();
        }
        MimeEmailParser parser = new MimeEmailParser();
        QuoteSignatureStripper stripper = new QuoteSignatureStripper();
        LanguageDetector languages = new LanguageDetector();
        List<String> ids = new ArrayList<>();
        List<Email> emails = new ArrayList<>();
        List<byte[]> raws = new ArrayList<>();
        Map<String, Set<String>> ngrams = new HashMap<>();
        for (Path file : files) {
            byte[] raw = Files.readAllBytes(file);
            Email email = parser.parse(raw, "uni");
            String id = file.getFileName().toString().replace(".eml", "");
            ids.add(id);
            emails.add(email);
            raws.add(raw);
            ngrams.put(id, ngrams(email.body()));
        }
        // A 6-word sequence counts as another email's only if it occurs in exactly one other email and not in this one.
        Map<String, Integer> owners = new HashMap<>();
        ngrams.values().forEach(set -> set.forEach(g -> owners.merge(g, 1, Integer::sum)));
        List<Row> rows = new ArrayList<>();
        StringBuilder drafts = new StringBuilder("# Drafts (W9, real mail; Git-ignored, contains Enron mail)\n");
        Path data = out.resolve("work");
        Files.createDirectories(data);
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(data.resolve("index"))) {
            PrototypeApp.Wiring wiring = PrototypeApp.Wiring.create(indexes, new PrototypeApp.Options(null, KNOWLEDGE, data,
                    "uni", PrototypeApp.Architecture.BLACKBOARD, AgendaStrategy.Settings.defaults().minContribution(), llm));
            for (int i = 0; i < emails.size(); i++) {
                Email email = emails.get(i);
                String id = ids.get(i);
                String header = new String(raws.get(i), StandardCharsets.UTF_8).split("\\r?\\n\\r?\\n", 2)[0];
                Set<String> senderNames = names(header, "From", "X-From");
                Set<String> recipientNames = names(header, "To", "X-To");
                recipientNames.removeAll(senderNames);
                String text = stripper.currentText(email.body() == null ? "" : email.body());
                String language = text.isBlank() ? "en" : languages.detect(text);
                long start = System.nanoTime();
                BlackboardSnapshot state = wiring.coordinator.run(wiring.coordinator.receive(email), PrototypeApp.BLACKBOARD_LIMITS);
                double millis = (System.nanoTime() - start) / 1e6;
                Draft last = state.drafts().isEmpty() ? null : state.drafts().get(state.drafts().size() - 1);
                String body = last == null ? "" : last.body();
                String contributors = String.join("+", Contribution.latest(state.evidence()).stream()
                        .map(Contribution::agentId).sorted().toList());
                String failure = state.failure().map(f -> f.kind() + ": " + f.message()).orElse("");
                ReplyChecks.Gold gold = new ReplyChecks.Gold(language, true, Optional.empty(), new ArrayList<>(recipientNames),
                        List.of(), List.of(), List.of(), false, "W9: no gold answer");
                List<String> violations = last == null ? List.of() : ReplyChecks.score(gold, body).violations();
                String greeting = body.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("")
                        .toLowerCase(Locale.ROOT);
                boolean greetsSender = senderNames.stream().anyMatch(n -> containsWord(greeting, n));
                boolean greetsRecipientOnly = !greetsSender && recipientNames.stream().anyMatch(n -> containsWord(greeting, n));
                String lower = body.toLowerCase(Locale.ROOT).replace('’', '\'');
                String emailLower = (email.body() == null ? "" : email.body()).toLowerCase(Locale.ROOT);
                List<String> promises = PROMISES.stream().filter(lower::contains).filter(p -> !emailLower.contains(p)).toList();
                List<String> university = UNIVERSITY.stream().filter(lower::contains).filter(p -> !emailLower.contains(p)).toList();
                Set<String> own = ngrams.get(id);
                boolean other = ngrams(body).stream().anyMatch(g -> !own.contains(g) && owners.getOrDefault(g, 0) == 1);
                Row row = new Row(id, state.status(), failure, millis, contributors, greetsSender, greetsRecipientOnly,
                        violations, promises, university, other, body.length());
                rows.add(row);
                drafts.append("\n## ").append(id).append(" (").append(state.status()).append(", by ").append(contributors)
                        .append(")\n\n**Email (current text):**\n\n").append(quote(text)).append("\n\n**Reply:**\n\n")
                        .append(last == null ? "_no draft: " + failure + "_" : quote(body)).append("\n\n**Flags:** ")
                        .append(flags(row)).append("\n");
                System.out.println("  " + id + ": " + state.status() + String.format(Locale.ROOT, " (%.0f ms) ", millis) + flags(row));
            }
        }
        Files.writeString(out.resolve("drafts.md"), drafts.toString());
        Files.writeString(out.resolve("rubric-sample.md"), rubric(rows, ids));
        return rows;
    }

    /** Lower-case name words of the named headers: display names and address parts, without domain words. */
    static Set<String> names(String header, String... fields) {
        Set<String> names = new LinkedHashSet<>();
        for (String field : fields) {
            Matcher m = Pattern.compile("(?mi)^" + Pattern.quote(field) + ":[ \\t]*(.*(?:\\r?\\n[ \\t].*)*)").matcher(header);
            if (m.find()) {
                String value = m.group(1).replaceAll("@[\\w.-]+", " ").replaceAll("<[^>]*>", " ").toLowerCase(Locale.ROOT);
                Matcher w = WORD.matcher(value.replace('.', ' ').replace('_', ' '));
                while (w.find()) {
                    if (!NOT_NAMES.contains(w.group())) {
                        names.add(w.group());
                    }
                }
            }
        }
        return names;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private static Set<String> ngrams(String text) {
        String[] words = (text == null ? "" : text).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9 ]", " ").trim().split("\\s+");
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + SHARED_WORDS <= words.length; i++) {
            grams.add(String.join(" ", Arrays.copyOfRange(words, i, i + SHARED_WORDS)));
        }
        return grams;
    }

    private static String flags(Row r) {
        List<String> flags = new ArrayList<>(r.violations());
        if (r.status() == BoardStatus.AWAITING_REVIEW && !r.greetsSender()) {
            flags.add(r.greetsRecipientOnly() ? "greets a recipient" : "sender not named in greeting");
        }
        r.promises().forEach(p -> flags.add("promise: " + p));
        r.university().forEach(p -> flags.add("university: " + p));
        if (r.otherEmailContent()) {
            flags.add("another email's content");
        }
        return flags.isEmpty() ? "none" : String.join("; ", flags);
    }

    private static String quote(String text) {
        return "> " + text.strip().replace("\n", "\n> ");
    }

    private static String rubric(List<Row> rows, List<String> ids) {
        List<String> drafted = new ArrayList<>(rows.stream().filter(r -> r.status() == BoardStatus.AWAITING_REVIEW).map(Row::id).toList());
        Collections.shuffle(drafted, new Random(20261111L));
        StringBuilder out = new StringBuilder("# Rubric sample (W9): 20 drafted replies, seed 20261111\n\n")
                .append("Read each in `drafts.md`. Score 0 (no), 1 (partly), 2 (yes):\n")
                .append("polite and appropriate | answers or acknowledges what was asked | invents no fact | admits what it cannot know\n\n")
                .append("| Email | Polite | Answers or acknowledges | Invents nothing | Admits limits | Note |\n| --- | --- | --- | --- | --- | --- |\n");
        drafted.stream().limit(20).sorted().forEach(id -> out.append("| ").append(id).append(" |  |  |  |  |  |\n"));
        return out.toString();
    }

    static String render(List<Row> rows, LlmSettings llm, Path dataDir) {
        List<Row> drafted = rows.stream().filter(r -> r.status() == BoardStatus.AWAITING_REVIEW).toList();
        Map<String, Integer> violations = new TreeMap<>();
        drafted.forEach(r -> r.violations().forEach(v -> violations.merge(v, 1, Integer::sum)));
        Map<String, Integer> failures = new TreeMap<>();
        rows.stream().filter(r -> r.status() != BoardStatus.AWAITING_REVIEW)
                .forEach(r -> failures.merge(r.failure().replaceAll(":.*", "").isBlank() ? r.status().toString()
                        : r.failure().length() > 90 ? r.failure().substring(0, 90) : r.failure(), 1, Integer::sum));
        Map<String, Integer> who = new TreeMap<>();
        rows.forEach(r -> who.merge(r.contributors().isBlank() ? "(none)" : r.contributors(), 1, Integer::sum));
        double[] ms = drafted.stream().mapToDouble(Row::millis).sorted().toArray();
        StringBuilder out = new StringBuilder("# Replies on Real Mail (benchmark plan W9)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z on `").append(dataDir).append("` (")
                .append(rows.size()).append(" Enron emails, frozen test set). Model: ").append(llm.describe())
                .append(". Blackboard architecture, university knowledge. No gold answer: only checks that need none. Counts and ")
                .append("email identifiers only; email and reply text stay in the Git-ignored run folder.\n\n")
                .append("| Measure | Value |\n| --- | --- |\n")
                .append("| Drafted | ").append(drafted.size()).append(" / ").append(rows.size()).append(" |\n")
                .append("| Latency p50 / p95 (drafted) | ").append(ms.length == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f s / %.1f s",
                        ms[(ms.length - 1) / 2] / 1000, ms[Math.min(ms.length - 1, (int) Math.ceil(0.95 * ms.length) - 1)] / 1000)).append(" |\n")
                .append("| Greeting names the sender | ").append(drafted.stream().filter(Row::greetsSender).count()).append(" / ").append(drafted.size()).append(" |\n")
                .append("| Greeting names only a recipient | ").append(drafted.stream().filter(Row::greetsRecipientOnly).count()).append(" |\n")
                .append("| Replies without any flag | ").append(drafted.stream().filter(r -> flags(r).equals("none")).count()).append(" / ").append(drafted.size()).append(" |\n")
                .append("| Promise or claimed action | ").append(drafted.stream().filter(r -> !r.promises().isEmpty()).count()).append(" |\n")
                .append("| University knowledge in a reply | ").append(drafted.stream().filter(r -> !r.university().isEmpty()).count()).append(" |\n")
                .append("| Another email's content (shared 6-word sequence) | ").append(drafted.stream().filter(Row::otherEmailContent).count()).append(" |\n\n")
                .append("| Check violation (reply checks with no facts) | Replies |\n| --- | --- |\n");
        violations.forEach((k, v) -> out.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        out.append("\n| Who answered | Emails |\n| --- | --- |\n");
        who.forEach((k, v) -> out.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        if (!failures.isEmpty()) {
            out.append("\n| Failure | Emails |\n| --- | --- |\n");
            failures.forEach((k, v) -> out.append("| ").append(k.replace("|", "/")).append(" | ").append(v).append(" |\n"));
        }
        out.append("\n| Email | Status | By | Flags |\n| --- | --- | --- | --- |\n");
        for (Row r : rows) {
            out.append("| ").append(r.id()).append(" | ").append(r.status()).append(" | ").append(r.contributors()).append(" | ")
                    .append(flags(r).replace("|", "/")).append(" |\n");
        }
        return out.toString();
    }
}
