package org.supermas.benchmark;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.supermas.context.IndexedDocument;
import org.supermas.context.LuceneContextRetriever;
import org.supermas.context.ProjectIndexes;
import org.supermas.metadata.EmailThreader;
import org.supermas.metadata.LanguageDetector;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.metadata.QuoteSignatureStripper;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextQuery;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;

/**
 * The C1 baseline: one retriever per mailbox, with each mailbox as a project. No labels exist at this
 * scale, so relevance is a proxy: earlier messages in the email's own thread, searched for with its new
 * text and not its subject. Writes counts and scores only, never email content.
 *
 * <p>Usage: C1Benchmark MAILDIR MIN_MESSAGES QUERIES_PER_PROJECT REPORT_FILE INDEX_DIR
 */
public final class C1Benchmark {
    private static final long SEED = 20261101L;
    private static final int K = 10;
    private static final int BOOTSTRAP_SAMPLES = 1000;
    private static final String SOURCE_SUFFIX = "-mailbox";

    private record Message(Email email, String threadId, String text, Instant at) { }

    private record Scores(double recall5, double recall10, double reciprocalRank, double ndcg10) { }

    private C1Benchmark() { }

    private static String agentOf(String project) {
        return "agent-" + project;
    }

    public static void main(String[] args) throws Exception {
        Path maildir = Path.of(args[0]);
        int minMessages = Integer.parseInt(args[1]);
        int queriesPerProject = Integer.parseInt(args[2]);
        Path report = Path.of(args[3]);
        Path indexDir = Path.of(args[4]);

        long started = System.nanoTime();
        Map<String, List<Message>> projects = new LinkedHashMap<>();
        Counts counts = new Counts();
        List<Path> mailboxes;
        try (Stream<Path> listing = Files.list(maildir)) {
            mailboxes = listing.filter(Files::isDirectory).sorted().toList();
        }
        for (Path mailbox : mailboxes) {
            if (counts.messages >= minMessages) {
                break;
            }
            List<Message> messages = load(mailbox, counts);
            projects.put(mailbox.getFileName().toString(), messages);
            counts.messages += messages.size();
        }
        double loadSeconds = (System.nanoTime() - started) / 1e9;

        Map<String, ContextSource> sources = new HashMap<>();
        Map<String, AgentConfig> agents = new HashMap<>();
        for (String project : projects.keySet()) {
            String source = project + SOURCE_SUFFIX;
            sources.put(source, new ContextSource(source, project, URI.create("file:///benchmark"), Set.of(agentOf(project))));
            agents.put(agentOf(project), new AgentConfig(agentOf(project), "benchmark", "", Set.of(), List.of(),
                    Set.of(source), false));
        }

        started = System.nanoTime();
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(indexDir)) {
            for (Map.Entry<String, List<Message>> project : projects.entrySet()) {
                List<IndexedDocument> documents = new ArrayList<>();
                for (Message message : project.getValue()) {
                    Scope scope = new Scope(project.getKey(), message.threadId());
                    String subject = message.email().subject() == null ? "" : message.email().subject();
                    documents.add(new IndexedDocument(message.email().id(), scope, project.getKey() + SOURCE_SUFFIX,
                            Optional.of(MessageKind.RECEIVED), new LanguageDetector().detect(message.text()), subject,
                            message.text(), Set.of(agentOf(project.getKey())), Set.of(), message.at()));
                }
                indexes.forProject(project.getKey()).addAll(documents);
                indexes.forProject(project.getKey()).commit();
            }
            double indexSeconds = (System.nanoTime() - started) / 1e9;

            LuceneContextRetriever retriever = new LuceneContextRetriever(indexes, sources,
                    id -> Optional.ofNullable(agents.get(id)));
            Files.createDirectories(report.toAbsolutePath().getParent());
            Files.writeString(report, run(projects, retriever, counts, queriesPerProject, loadSeconds, indexSeconds));
            System.out.println("Report written to " + report);
        }
    }

    private static final class Counts {
        int messages;
        int unparseable;
        int duplicates;
    }

    private static List<Message> load(Path mailbox, Counts counts) throws Exception {
        MimeEmailParser parser = new MimeEmailParser();
        QuoteSignatureStripper stripper = new QuoteSignatureStripper();
        Map<String, Email> emails = new LinkedHashMap<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(mailbox)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            try {
                Email email = parser.parse(Files.readAllBytes(file), mailbox.getFileName().toString());
                // Enron gives each filed copy its own Message-ID, so copies are recognized by content.
                String content = email.sender() + "\n" + email.receivedAt() + "\n" + email.subject() + "\n" + email.body();
                if (email.id() == null || email.receivedAt() == null) {
                    counts.unparseable++;
                } else if (emails.putIfAbsent(content, email) != null) {
                    counts.duplicates++;
                }
            } catch (Exception e) {
                counts.unparseable++;
            }
        }
        Map<String, String> threads = new EmailThreader(true).assignThreadIds(new ArrayList<>(emails.values()));
        // Numbered within each mailbox, so equal thread identifiers exist across projects on purpose.
        Map<String, String> numbered = new HashMap<>();
        List<Message> messages = new ArrayList<>();
        for (Email email : emails.values()) {
            String body = email.body() == null ? "" : email.body();
            String thread = numbered.computeIfAbsent(threads.get(email.id()), id -> "thread-" + numbered.size());
            messages.add(new Message(email, thread, stripper.currentText(body), email.receivedAt()));
        }
        return messages;
    }

    private static String run(Map<String, List<Message>> projects, LuceneContextRetriever retriever, Counts counts,
                              int queriesPerProject, double loadSeconds, double indexSeconds) {
        List<Scores> c1 = new ArrayList<>();
        List<Scores> recency = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        List<String> projectIds = new ArrayList<>(projects.keySet());
        int violations = 0;
        int crossProjectProbes = 0;
        int threadProbes = 0;
        int sharedThreadIds = 0;
        Random random = new Random(SEED);

        Map<String, Set<String>> threadIdsByProject = new HashMap<>();
        Map<String, Set<String>> idsByProject = new HashMap<>();
        projects.forEach((project, messages) -> {
            threadIdsByProject.put(project, new HashSet<>(messages.stream().map(Message::threadId).toList()));
            idsByProject.put(project, new HashSet<>(messages.stream().map(m -> m.email().id()).toList()));
        });

        for (int p = 0; p < projectIds.size(); p++) {
            String project = projectIds.get(p);
            String other = projectIds.get((p + 1) % projectIds.size());
            List<Message> messages = projects.get(project);
            List<Message> byTime = new ArrayList<>(messages);
            byTime.sort((a, b) -> a.at().compareTo(b.at()));

            Map<String, List<Message>> threads = new HashMap<>();
            for (Message message : messages) {
                threads.computeIfAbsent(message.threadId(), id -> new ArrayList<>()).add(message);
            }
            List<Message> candidates = new ArrayList<>();
            for (Message message : messages) {
                boolean hasEarlier = threads.get(message.threadId()).stream()
                        .anyMatch(m -> m.at().isBefore(message.at()));
                if (hasEarlier && !message.text().isBlank()) {
                    candidates.add(message);
                }
            }
            Collections.shuffle(candidates, random);

            for (Message query : candidates.subList(0, Math.min(queriesPerProject, candidates.size()))) {
                Scope scope = new Scope(project, query.threadId());
                BoardId board = new BoardId(scope, "benchmark");
                Set<String> relevant = new HashSet<>();
                for (Message m : threads.get(query.threadId())) {
                    if (m.at().isBefore(query.at())) {
                        relevant.add(m.email().id());
                    }
                }

                long start = System.nanoTime();
                List<Evidence> found = retriever.retrieveDomain(new ContextQuery(board, agentOf(project),
                        Set.of(project + SOURCE_SUFFIX), List.of(query.text()), true, Integer.MAX_VALUE)).evidence();
                latencies.add(System.nanoTime() - start);

                List<String> ranked = new ArrayList<>();
                for (Evidence evidence : found) {
                    if (!idsByProject.get(project).contains(evidence.id()) || !evidence.scope().equals(scope)
                            || !evidence.permittedAgentIds().equals(Set.of(agentOf(project)))) {
                        violations++;
                    }
                    if (evidence.provenance().recordedAt().isBefore(query.at())) {
                        ranked.add(evidence.id());  // Leaves out the query itself and the future.
                    }
                }
                c1.add(score(ranked, relevant));

                List<String> latest = new ArrayList<>();
                for (int i = byTime.size() - 1; i >= 0 && latest.size() < K; i--) {
                    if (byTime.get(i).at().isBefore(query.at())) {
                        latest.add(byTime.get(i).email().id());
                    }
                }
                recency.add(score(latest, relevant));

                // This agent asks for the other project's source, and the other project's agent asks here.
                if (!project.equals(other)) {
                    crossProjectProbes += 2;
                    violations += retriever.retrieveDomain(new ContextQuery(board, agentOf(project),
                            Set.of(other + SOURCE_SUFFIX), List.of(query.text()), true, Integer.MAX_VALUE)).evidence().size();
                    violations += retriever.retrieveDomain(new ContextQuery(board, agentOf(other),
                            Set.of(project + SOURCE_SUFFIX), List.of(query.text()), true, Integer.MAX_VALUE)).evidence().size();
                }

                // Exact thread history must stay inside the thread.
                threadProbes++;
                Set<String> members = new HashSet<>(threads.get(query.threadId()).stream().map(m -> m.email().id()).toList());
                for (Evidence evidence : retriever.retrieveThread(board,
                        new HistoryQuery(Set.of(MessageKind.values()), 1000)).evidence()) {
                    if (!members.contains(evidence.id()) || !evidence.scope().equals(scope)) {
                        violations++;
                    }
                }
            }

            // The same message in two mailboxes gives equal thread identifiers in different projects.
            if (!project.equals(other)) {
                Set<String> shared = new HashSet<>(threadIdsByProject.get(project));
                shared.retainAll(threadIdsByProject.get(other));
                sharedThreadIds += shared.size();
                for (String thread : shared.stream().sorted().limit(200).toList()) {
                    threadProbes++;
                    Set<String> members = new HashSet<>(threads.get(thread).stream().map(m -> m.email().id()).toList());
                    for (Evidence evidence : retriever.retrieveThread(new BoardId(new Scope(project, thread), "benchmark"),
                            new HistoryQuery(Set.of(MessageKind.values()), 100000)).evidence()) {
                        if (!members.contains(evidence.id()) || !evidence.scope().projectId().equals(project)) {
                            violations++;
                        }
                    }
                }
            }
        }

        int threadsTotal = threadIdsByProject.values().stream().mapToInt(Set::size).sum();
        StringBuilder out = new StringBuilder("# C1 Baseline on Enron\n\n");
        out.append("Retrieval quality uses a proxy, not human relevance labels. See `docs/reports/step-3.5.md`.\n\n");
        out.append("| Item | Value |\n| --- | --- |\n");
        out.append(row("Projects (mailboxes)", projects.size()));
        out.append(row("Unique messages indexed", counts.messages));
        out.append(row("Threads, with subject fallback", threadsTotal));
        out.append(row("Duplicates removed", counts.duplicates));
        out.append(row("Unparseable or undated, skipped", counts.unparseable));
        out.append(row("Queries", c1.size()));
        out.append(row("Load, strip and thread", String.format(Locale.ROOT, "%.0f s", loadSeconds)));
        out.append(row("Index build", String.format(Locale.ROOT, "%.1f s", indexSeconds)));
        out.append(row("Retrieval latency p50", milliseconds(BenchmarkRunner.percentile(latencies, 50))));
        out.append(row("Retrieval latency p95", milliseconds(BenchmarkRunner.percentile(latencies, 95))));
        out.append("\n## Retrieval, with 95% bootstrap intervals\n\n");
        out.append("| Metric | C1 (Lucene) | Recency baseline |\n| --- | --- | --- |\n");
        metric(out, "Recall@5", c1, recency, Scores::recall5);
        metric(out, "Recall@10", c1, recency, Scores::recall10);
        metric(out, "MRR", c1, recency, Scores::reciprocalRank);
        metric(out, "nDCG@10", c1, recency, Scores::ndcg10);
        out.append("\n## Isolation\n\n| Check | Value |\n| --- | --- |\n");
        out.append(row("Cross-project retrieval probes", crossProjectProbes));
        out.append(row("Thread history probes", threadProbes));
        out.append(row("Thread identifiers shared with the next mailbox", sharedThreadIds));
        out.append(row("**Violations**", violations));
        return out.toString();
    }

    private static Scores score(List<String> ranked, Set<String> relevant) {
        int hits5 = 0;
        int hits10 = 0;
        double reciprocal = 0;
        double dcg = 0;
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                hits5 += i < 5 ? 1 : 0;
                hits10 += i < K ? 1 : 0;
                if (reciprocal == 0) {
                    reciprocal = 1.0 / (i + 1);
                }
                if (i < K) {
                    dcg += 1 / (Math.log(i + 2) / Math.log(2));
                }
            }
        }
        double ideal = 0;
        for (int i = 0; i < Math.min(relevant.size(), K); i++) {
            ideal += 1 / (Math.log(i + 2) / Math.log(2));
        }
        return new Scores((double) hits5 / relevant.size(), (double) hits10 / relevant.size(), reciprocal, dcg / ideal);
    }

    private static void metric(StringBuilder out, String name, List<Scores> c1, List<Scores> recency,
                               java.util.function.ToDoubleFunction<Scores> value) {
        out.append("| ").append(name).append(" | ").append(interval(c1, value)).append(" | ")
                .append(interval(recency, value)).append(" |\n");
    }

    private static String interval(List<Scores> scores, java.util.function.ToDoubleFunction<Scores> value) {
        double[] values = scores.stream().mapToDouble(value).toArray();
        double mean = java.util.Arrays.stream(values).average().orElse(0);
        double[] means = new double[BOOTSTRAP_SAMPLES];
        Random random = new Random(SEED);
        for (int s = 0; s < BOOTSTRAP_SAMPLES; s++) {
            double sum = 0;
            for (int i = 0; i < values.length; i++) {
                sum += values[random.nextInt(values.length)];
            }
            means[s] = sum / values.length;
        }
        java.util.Arrays.sort(means);
        return String.format(Locale.ROOT, "%.3f (%.3f-%.3f)", mean, means[(int) (BOOTSTRAP_SAMPLES * 0.025)],
                means[(int) (BOOTSTRAP_SAMPLES * 0.975)]);
    }

    private static String row(String name, Object value) {
        return "| " + name + " | " + value + " |\n";
    }

    private static String milliseconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f ms", nanos / 1_000_000.0);
    }
}
