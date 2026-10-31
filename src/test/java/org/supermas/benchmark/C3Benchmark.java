package org.supermas.benchmark;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
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
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import org.supermas.benchmark.C1Benchmark.Message;
import org.supermas.benchmark.C1Benchmark.Scores;
import org.supermas.context.CorrespondentSettings;
import org.supermas.context.IndexedDocument;
import org.supermas.context.LuceneContextRetriever;
import org.supermas.context.ProjectIndexes;
import org.supermas.context.SenderVerifier;
import org.supermas.context.TopicCandidateFilter;
import org.supermas.context.TopicContextAgent;
import org.supermas.context.TopicContextSelector;
import org.supermas.context.TopicDefinition;
import org.supermas.context.TopicEvidence;
import org.supermas.context.TopicTagger;
import org.supermas.context.TopicType;
import org.supermas.metadata.LanguageDetector;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextQuery;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;
import org.supermas.prototype.InMemoryBlackboard;

/**
 * C3 against C1 on the mailboxes of the C1 baseline. Quality uses two proxies, because no relevance labels
 * exist at this scale. Writes counts and scores only, never email content or folder names.
 *
 * <p>The settings below were fixed before the first run and must not be tuned to these results.
 *
 * <p>Usage: C3Benchmark MAILDIR MIN_MESSAGES QUERIES_PER_PROJECT REPORT_FILE INDEX_DIR
 */
public final class C3Benchmark {
    private static final int MAX_SUBJECTS = 3;
    private static final int MIN_FOLDER_MESSAGES = 20;
    private static final int MIN_KEYWORD_LENGTH = 4;
    private static final int BOOTSTRAP_SAMPLES = 1000;
    private static final int NO_LIMIT = Integer.MAX_VALUE;
    private static final String READER = "reader";
    private static final String DISCUSSION = "discussion";
    private static final String CORRESPONDENT = "correspondent";
    private static final SenderVerifier SIMULATED_VERIFIED = email -> true;
    /** Folders that mail systems create, or that hold no single subject. */
    private static final Set<String> GENERIC_FOLDERS = Set.of("all_documents", "sent", "sent_items", "_sent_mail",
            "inbox", "notes_inbox", "deleted_items", "discussion_threads", "archive", "archiving", "calendar", "contacts",
            "tasks", "notes", "personal", "misc", "junk", "to_do", "drafts", "attachments", "e-mail", "online", "projects",
            "general", "other", "miscellaneous", "important", "esource", "corporate", "resumes", "old", "new", "various");

    private C3Benchmark() { }

    private record Project(String id, String source, List<Message> messages, List<TopicContextAgent> subjects,
                           TopicContextAgent discussion, TopicContextAgent correspondent,
                           TopicContextAgent forgedCorrespondent, AgentConfig reader, Map<String, Set<String>> declaredFolders) { }

    private static final class Variant {
        final List<Scores> threadProxy = new ArrayList<>();
        final List<Scores> folderProxy = new ArrayList<>();
    }

    private static final class Stats {
        int violations;
        int crossProjectProbes;
        int forgedSenderProbes;
        long agentRuns;
        long boards;
        long entries;
        long uniqueDocuments;
        final List<Long> latencies = new ArrayList<>();
    }

    public static void main(String[] args) throws Exception {
        Path maildir = Path.of(args[0]);
        int minMessages = Integer.parseInt(args[1]);
        int queriesPerProject = Integer.parseInt(args[2]);
        Path report = Path.of(args[3]);
        Path indexDir = Path.of(args[4]);

        Map<String, List<Message>> loaded = new LinkedHashMap<>();
        C1Benchmark.Counts counts = new C1Benchmark.Counts();
        List<Path> mailboxes;
        try (Stream<Path> listing = Files.list(maildir)) {
            mailboxes = listing.filter(Files::isDirectory).sorted().toList();
        }
        for (Path mailbox : mailboxes) {
            if (counts.messages >= minMessages) {
                break;
            }
            List<Message> messages = C1Benchmark.load(mailbox, counts);
            loaded.put(mailbox.getFileName().toString(), messages);
            counts.messages += messages.size();
        }

        Map<String, ContextSource> sources = new HashMap<>();
        Map<String, AgentConfig> agents = new HashMap<>();
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(indexDir)) {
            // The retriever copies its sources when built, so they must all be known first.
            Map<String, List<TopicDefinition>> definitions = new HashMap<>();
            Map<String, Map<String, Set<String>>> declared = new HashMap<>();
            Map<String, Set<String>> readers = new HashMap<>();
            for (Map.Entry<String, List<Message>> entry : loaded.entrySet()) {
                String project = entry.getKey();
                declared.put(project, new HashMap<>());
                definitions.put(project, declare(project, entry.getValue(), declared.get(project)));
                Set<String> everyone = new HashSet<>();
                definitions.get(project).forEach(definition -> everyone.add(definition.agentId()));
                everyone.addAll(List.of(project + "-" + DISCUSSION, project + "-" + CORRESPONDENT, project + "-" + READER));
                readers.put(project, everyone);
                sources.put(project + "-mailbox", new ContextSource(project + "-mailbox", project,
                        URI.create("file:///benchmark"), everyone));
            }
            LuceneContextRetriever retriever = new LuceneContextRetriever(indexes, sources,
                    id -> Optional.ofNullable(agents.get(id)));
            Map<String, Project> projects = new LinkedHashMap<>();
            for (Map.Entry<String, List<Message>> entry : loaded.entrySet()) {
                String project = entry.getKey();
                projects.put(project, build(project, entry.getValue(), retriever, agents, indexes,
                        definitions.get(project), declared.get(project), readers.get(project)));
            }
            Files.createDirectories(report.toAbsolutePath().getParent());
            Files.writeString(report, run(projects, retriever, counts, queriesPerProject));
            System.out.println("Report written to " + report);
        }
    }

    /** Subject topics from folder names, with fixed selection rules. */
    private static List<TopicDefinition> declare(String project, List<Message> messages, Map<String, Set<String>> declared) {
        String source = project + "-mailbox";
        Map<String, Integer> sizes = new HashMap<>();
        messages.forEach(message -> message.folders().forEach(folder -> sizes.merge(folder, 1, Integer::sum)));
        List<TopicDefinition> definitions = new ArrayList<>();
        sizes.keySet().stream().sorted().forEach(folder -> {
            String name = folder.substring(folder.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            List<String> keywords = Stream.of(name.split("[^a-z]+")).filter(word -> word.length() >= MIN_KEYWORD_LENGTH).toList();
            if (sizes.get(folder) >= MIN_FOLDER_MESSAGES && !GENERIC_FOLDERS.contains(name) && !keywords.isEmpty()) {
                String id = project + "-subject-" + definitions.size();
                definitions.add(new TopicDefinition(TopicType.SUBJECT, id, project, id, keywords, Set.of(source), NO_LIMIT));
                declared.put(folder, Set.of(id));
            }
        });
        return definitions;
    }

    private static Project build(String project, List<Message> messages, LuceneContextRetriever retriever,
                                 Map<String, AgentConfig> agents, ProjectIndexes indexes, List<TopicDefinition> definitions,
                                 Map<String, Set<String>> declared, Set<String> everyone) {
        String source = project + "-mailbox";
        String discussionId = project + "-" + DISCUSSION;
        String correspondentId = project + "-" + CORRESPONDENT;
        String reader = project + "-" + READER;

        List<TopicContextAgent> subjects = definitions.stream().map(definition -> new TopicContextAgent(definition, retriever)).toList();
        TopicDefinition correspondentTopic = new TopicDefinition(TopicType.CORRESPONDENT, correspondentId, project, "",
                List.of(), Set.of(source), NO_LIMIT);
        TopicContextAgent discussion = new TopicContextAgent(new TopicDefinition(TopicType.DISCUSSION, discussionId, project, "",
                List.of(), Set.of(source), NO_LIMIT), retriever);
        TopicContextAgent correspondent = new TopicContextAgent(correspondentTopic, retriever,
                new CorrespondentSettings(true, SIMULATED_VERIFIED));
        TopicContextAgent forged = new TopicContextAgent(correspondentTopic, retriever,
                new CorrespondentSettings(true, SenderVerifier.none()));
        AgentConfig readerConfig = new AgentConfig(reader, "benchmark", "", Set.of(), List.of(), Set.of(source), false);
        subjects.forEach(agent -> agents.put(agent.config().id(), agent.config()));
        agents.put(discussionId, discussion.config());
        agents.put(correspondentId, correspondent.config());
        agents.put(reader, readerConfig);

        TopicTagger tagger = new TopicTagger(definitions);
        List<IndexedDocument> documents = new ArrayList<>();
        LanguageDetector language = new LanguageDetector();
        for (Message message : messages) {
            String subject = message.email().subject() == null ? "" : message.email().subject();
            Set<String> tags = new HashSet<>(tagger.tagsFor(project, subject, message.text()));
            TopicTagger.correspondentTag(message.email(), SIMULATED_VERIFIED).ifPresent(tags::add);
            documents.add(new IndexedDocument(message.email().id(), new Scope(project, message.threadId()), source,
                    Optional.of(MessageKind.RECEIVED), language.detect(message.text()), subject, message.text(), everyone, tags,
                    message.at()));
        }
        indexes.forProject(project).addAll(documents);
        indexes.forProject(project).commit();
        return new Project(project, source, messages, subjects, discussion, correspondent, forged, readerConfig, declared);
    }

    private static InMemoryBlackboard board(Scope scope, Message query) {
        Email email = new Email(query.email().id(), scope, query.email().sender(), List.of(), "", query.text(), query.at(), Map.of());
        // No subject and no subject keywords, so every variant receives the query that C1 receives.
        Analysis analysis = new Analysis(List.of(), List.of(), Map.of(MetadataAgent.KEY_CURRENT_TEXT, query.text()));
        return new InMemoryBlackboard(new BoardId(scope, "benchmark"), email, analysis, Clock.fixed(query.at(), ZoneOffset.UTC));
    }

    /** Runs the selected agents and the merge, as the pipeline would, and checks every result for isolation. */
    private static List<String> topics(List<TopicContextAgent> agents, Scope scope, Message query, Project project,
                                       Set<String> projectIds, Stats stats, boolean record) {
        InMemoryBlackboard board = board(scope, query);
        long start = System.nanoTime();
        List<TopicContextAgent> selected = new TopicCandidateFilter(MAX_SUBJECTS).select(agents, board);
        for (TopicContextAgent agent : selected) {
            agent.execute(null, board.accessFor(agent.config()));
        }
        List<Evidence> merged = new TopicContextSelector().select(board, project.reader(), NO_LIMIT).evidence();
        if (record) {
            stats.latencies.add(System.nanoTime() - start);
            stats.agentRuns += selected.size();
            stats.boards++;
            stats.entries += board.snapshot().evidence().size();
            stats.uniqueDocuments += merged.size();
        }
        List<String> ranked = new ArrayList<>();
        for (Evidence item : merged) {
            if (!projectIds.contains(item.id()) || !item.scope().equals(scope)
                    || !item.permittedAgentIds().contains(project.reader().id())) {
                stats.violations++;
            }
            ranked.add(item.id());
        }
        return ranked;
    }

    private static void probes(Project project, Project other, Scope scope, Message query, Stats stats) {
        // A forged sender: a verifier that rejects the email must yield nothing cross-thread.
        InMemoryBlackboard board = board(scope, query);
        stats.forgedSenderProbes++;
        project.forgedCorrespondent().execute(null, board.accessFor(project.forgedCorrespondent().config()));
        stats.violations += board.snapshot().evidence().size();
        if (project == other) {
            return;
        }
        // Another project's agents on this board, and that project's reader of what this project's agents found.
        for (TopicContextAgent foreign : List.of(other.discussion(), other.correspondent())) {
            InMemoryBlackboard probe = board(scope, query);
            stats.crossProjectProbes++;
            foreign.execute(null, probe.accessFor(foreign.config()));
            stats.violations += probe.snapshot().evidence().size();
        }
        InMemoryBlackboard own = board(scope, query);
        project.discussion().execute(null, own.accessFor(project.discussion().config()));
        stats.crossProjectProbes++;
        stats.violations += new TopicContextSelector().select(own, other.reader(), NO_LIMIT).evidence().size();
    }

    private static List<String> keep(List<String> ranked, Map<String, Message> byId, Message query, boolean dropOwnThread) {
        List<String> kept = new ArrayList<>();
        for (String id : ranked) {
            Message found = byId.get(id);
            if (found != null && found.at().isBefore(query.at())
                    && !(dropOwnThread && found.threadId().equals(query.threadId()))) {
                kept.add(id);
            }
        }
        return kept;
    }

    private static String run(Map<String, Project> projects, LuceneContextRetriever retriever, C1Benchmark.Counts counts,
                              int queriesPerProject) {
        Map<String, Variant> variants = new LinkedHashMap<>();
        for (String name : List.of("C1", "Recency", "C3 subject only", "C3", "C3 + correspondent (simulated)", "Thread only")) {
            variants.put(name, new Variant());
        }
        Stats stats = new Stats();
        Random threadRandom = new Random(C1Benchmark.SEED);
        Random folderRandom = new Random(C1Benchmark.SEED + 1);
        List<String> ids = new ArrayList<>(projects.keySet());
        int subjectsTotal = 0;
        int folderQueries = 0;
        for (int p = 0; p < ids.size(); p++) {
            Project project = projects.get(ids.get(p));
            Project other = projects.get(ids.get((p + 1) % ids.size()));
            subjectsTotal += project.subjects().size();
            Map<String, Message> byId = new HashMap<>();
            project.messages().forEach(message -> byId.put(message.email().id(), message));
            Map<String, List<Message>> threads = new HashMap<>();
            for (Message message : project.messages()) {
                threads.computeIfAbsent(message.threadId(), id -> new ArrayList<>()).add(message);
            }
            List<Message> byTime = new ArrayList<>(project.messages());
            byTime.sort((a, b) -> a.at().compareTo(b.at()));

            // The same selection and shuffle as C1Benchmark, so the thread queries are the same queries.
            List<Message> threadQueries = new ArrayList<>();
            for (Message message : project.messages()) {
                boolean hasEarlier = threads.get(message.threadId()).stream().anyMatch(m -> m.at().isBefore(message.at()));
                if (hasEarlier && !message.text().isBlank()) {
                    threadQueries.add(message);
                }
            }
            Collections.shuffle(threadQueries, threadRandom);
            threadQueries = threadQueries.subList(0, Math.min(queriesPerProject, threadQueries.size()));

            List<Message> folderCandidates = new ArrayList<>();
            for (Message message : project.messages()) {
                if (!message.text().isBlank() && !folderRelevant(message, project).isEmpty()) {
                    folderCandidates.add(message);
                }
            }
            Collections.shuffle(folderCandidates, folderRandom);
            folderCandidates = folderCandidates.subList(0, Math.min(queriesPerProject, folderCandidates.size()));
            folderQueries += folderCandidates.size();

            for (Message query : threadQueries) {
                Set<String> relevant = new HashSet<>();
                threads.get(query.threadId()).stream().filter(m -> m.at().isBefore(query.at()))
                        .forEach(m -> relevant.add(m.email().id()));
                Map<String, List<String>> ranked = rankings(project, query, retriever, byId, byTime, stats, false, true);
                ranked.forEach((name, list) -> variants.get(name).threadProxy.add(C1Benchmark.score(list, relevant)));
                probes(project, other, new Scope(project.id(), query.threadId()), query, stats);
            }
            for (Message query : folderCandidates) {
                Set<String> relevant = folderRelevant(query, project);
                Map<String, List<String>> ranked = rankings(project, query, retriever, byId, byTime, stats, true, false);
                ranked.forEach((name, list) -> {
                    if (!name.equals("Thread only")) {
                        variants.get(name).folderProxy.add(C1Benchmark.score(list, relevant));
                    }
                });
            }
        }
        return report(projects, counts, variants, stats, subjectsTotal, folderQueries);
    }

    /** Earlier messages sharing a declared folder with the query, in other threads. */
    private static Set<String> folderRelevant(Message query, Project project) {
        Set<String> folders = new HashSet<>(query.folders());
        folders.retainAll(project.declaredFolders().keySet());
        Set<String> relevant = new HashSet<>();
        if (folders.isEmpty()) {
            return relevant;
        }
        for (Message other : project.messages()) {
            if (other.at().isBefore(query.at()) && !other.threadId().equals(query.threadId())
                    && other.folders().stream().anyMatch(folders::contains)) {
                relevant.add(other.email().id());
            }
        }
        return relevant;
    }

    private static Map<String, List<String>> rankings(Project project, Message query, LuceneContextRetriever retriever,
                                                      Map<String, Message> byId, List<Message> byTime, Stats stats,
                                                      boolean dropOwnThread, boolean record) {
        Scope scope = new Scope(project.id(), query.threadId());
        BoardId boardId = new BoardId(scope, "benchmark");
        Set<String> projectIds = byId.keySet();
        Map<String, List<String>> ranked = new LinkedHashMap<>();
        List<String> c1 = new ArrayList<>();
        for (Evidence item : retriever.retrieveDomain(new ContextQuery(boardId, project.reader().id(), Set.of(project.source()),
                List.of(query.text()), true, NO_LIMIT)).evidence()) {
            c1.add(item.id());
        }
        ranked.put("C1", keep(c1, byId, query, dropOwnThread));
        List<String> latest = new ArrayList<>();
        for (int i = byTime.size() - 1; i >= 0 && latest.size() < C1Benchmark.K; i--) {
            Message m = byTime.get(i);
            if (m.at().isBefore(query.at()) && !(dropOwnThread && m.threadId().equals(query.threadId()))) {
                latest.add(m.email().id());
            }
        }
        ranked.put("Recency", latest);
        ranked.put("C3 subject only", keep(topics(project.subjects(), scope, query, project, projectIds, stats, false),
                byId, query, dropOwnThread));
        List<TopicContextAgent> c3 = new ArrayList<>(project.subjects());
        c3.add(project.discussion());
        ranked.put("C3", keep(topics(c3, scope, query, project, projectIds, stats, record), byId, query, dropOwnThread));
        List<TopicContextAgent> all = new ArrayList<>(c3);
        all.add(project.correspondent());
        ranked.put("C3 + correspondent (simulated)", keep(topics(all, scope, query, project, projectIds, stats, false),
                byId, query, dropOwnThread));
        ranked.put("Thread only", keep(topics(List.of(project.discussion()), scope, query, project, projectIds, stats, false),
                byId, query, dropOwnThread));
        return ranked;
    }

    private static String report(Map<String, Project> projects, C1Benchmark.Counts counts, Map<String, Variant> variants,
                                 Stats stats, int subjects, int folderQueries) {
        StringBuilder out = new StringBuilder("# C3 Against C1 on Enron\n\n");
        out.append("Quality uses two proxies, not human relevance labels. See `docs/reports/step-4.10.md`.\n\n");
        out.append("| Item | Value |\n| --- | --- |\n");
        out.append(C1Benchmark.row("Projects (mailboxes)", projects.size()));
        out.append(C1Benchmark.row("Unique messages indexed", counts.messages));
        out.append(C1Benchmark.row("Declared subject topics, from folder names", subjects));
        out.append(C1Benchmark.row("Thread-proxy queries", variants.get("C1").threadProxy.size()));
        out.append(C1Benchmark.row("Folder-proxy queries", folderQueries));
        out.append(C1Benchmark.row("Maximum subject agents per email", MAX_SUBJECTS));
        out.append(C1Benchmark.row("Agents run per email, C3", String.format(Locale.ROOT, "%.2f", (double) stats.agentRuns / stats.boards)));
        out.append(C1Benchmark.row("Evidence entries per email before merging, C3", String.format(Locale.ROOT, "%.2f", (double) stats.entries / stats.boards)));
        out.append(C1Benchmark.row("Documents per email after merging, C3", String.format(Locale.ROOT, "%.2f", (double) stats.uniqueDocuments / stats.boards)));
        out.append(C1Benchmark.row("Duplicate rate before merging, C3", String.format(Locale.ROOT, "%.3f", 1 - (double) stats.uniqueDocuments / Math.max(1, stats.entries))));
        out.append(C1Benchmark.row("Duplicate rate after merging", "0 by construction"));
        out.append(C1Benchmark.row("C3 agents and merge latency p50", C1Benchmark.milliseconds(BenchmarkRunner.percentile(stats.latencies, 50))));
        out.append(C1Benchmark.row("C3 agents and merge latency p95", C1Benchmark.milliseconds(BenchmarkRunner.percentile(stats.latencies, 95))));

        out.append("\n## Thread proxy: earlier messages in the same thread\n\n");
        out.append("Thread only and every C3 variant containing the discussion agent find these by identifier, so their score here is high by construction.\n\n");
        table(out, variants, false);
        out.append("\n## Folder proxy: earlier messages in the same declared folder, in other threads\n\n");
        out.append("Results from the email's own thread are removed before scoring, so no variant benefits from thread identifiers.\n\n");
        table(out, variants, true);

        out.append("\n## Paired differences from C1, with 95% bootstrap intervals\n\n");
        out.append("| Proxy | Variant | Recall@10 | nDCG@10 |\n| --- | --- | --- | --- |\n");
        for (String name : List.of("C3 subject only", "C3", "C3 + correspondent (simulated)")) {
            out.append("| Thread | ").append(name).append(" | ")
                    .append(difference(variants.get(name).threadProxy, variants.get("C1").threadProxy, Scores::recall10)).append(" | ")
                    .append(difference(variants.get(name).threadProxy, variants.get("C1").threadProxy, Scores::ndcg10)).append(" |\n");
        }
        for (String name : List.of("C3 subject only", "C3", "C3 + correspondent (simulated)")) {
            out.append("| Folder | ").append(name).append(" | ")
                    .append(difference(variants.get(name).folderProxy, variants.get("C1").folderProxy, Scores::recall10)).append(" | ")
                    .append(difference(variants.get(name).folderProxy, variants.get("C1").folderProxy, Scores::ndcg10)).append(" |\n");
        }

        out.append("\n## Isolation\n\nZero violations mean something only if retrieval returns results, which the scores above show.\n\n| Check | Value |\n| --- | --- |\n");
        out.append(C1Benchmark.row("Cross-project probes", stats.crossProjectProbes));
        out.append(C1Benchmark.row("Forged-sender probes", stats.forgedSenderProbes));
        out.append(C1Benchmark.row("**Violations**", stats.violations));
        return out.toString();
    }

    private static void table(StringBuilder out, Map<String, Variant> variants, boolean folder) {
        out.append("| Variant | Recall@5 | Recall@10 | MRR | nDCG@10 |\n| --- | --- | --- | --- | --- |\n");
        variants.forEach((name, variant) -> {
            List<Scores> scores = folder ? variant.folderProxy : variant.threadProxy;
            if (!scores.isEmpty()) {
                out.append("| ").append(name).append(" | ").append(C1Benchmark.interval(scores, Scores::recall5)).append(" | ")
                        .append(C1Benchmark.interval(scores, Scores::recall10)).append(" | ")
                        .append(C1Benchmark.interval(scores, Scores::reciprocalRank)).append(" | ")
                        .append(C1Benchmark.interval(scores, Scores::ndcg10)).append(" |\n");
            }
        });
    }

    private static String difference(List<Scores> a, List<Scores> b, ToDoubleFunction<Scores> value) {
        double[] differences = new double[a.size()];
        double total = 0;
        for (int i = 0; i < a.size(); i++) {
            differences[i] = value.applyAsDouble(a.get(i)) - value.applyAsDouble(b.get(i));
            total += differences[i];
        }
        double[] means = new double[BOOTSTRAP_SAMPLES];
        Random random = new Random(C1Benchmark.SEED);
        for (int s = 0; s < BOOTSTRAP_SAMPLES; s++) {
            double sum = 0;
            for (int i = 0; i < differences.length; i++) {
                sum += differences[random.nextInt(differences.length)];
            }
            means[s] = sum / differences.length;
        }
        java.util.Arrays.sort(means);
        return String.format(Locale.ROOT, "%+.3f (%+.3f to %+.3f)", total / a.size(), means[(int) (BOOTSTRAP_SAMPLES * 0.025)],
                means[(int) (BOOTSTRAP_SAMPLES * 0.975)]);
    }
}
