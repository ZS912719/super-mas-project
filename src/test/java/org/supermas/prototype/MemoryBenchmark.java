package org.supermas.prototype;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.supermas.context.ProjectIndexes;
import org.supermas.control.AgendaStrategy;
import org.supermas.llm.LlmSettings;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Benchmark plan C14: is a thread's history found again after a restart, and never across projects?
 *
 * <p>Phase 1 processes every email of {@code synthetic-dev} under a second project, {@code other}, with the same
 * thread identifiers (a leak trap). Phase 2 processes the same emails under {@code uni}, each one in a freshly
 * created wiring on the same data folder, as after a restart. For every email the thread messages on its board
 * are compared with the earlier emails of its gold thread. Template generation; writes identifiers and counts only.
 *
 * <p>Usage: {@code MemoryBenchmark REPORT_FILE WORK_DIR}
 */
public final class MemoryBenchmark {
    private static final Path DEV = Path.of("benchmark/data/synthetic-dev");
    private static final Path KNOWLEDGE = Path.of("src/test/resources/university/knowledge");
    private static final String THREAD_MESSAGE = "Thread message";

    private MemoryBenchmark() { }

    record Row(String email, Set<String> expected, Set<String> found, Set<String> otherThread, int otherProject) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: MemoryBenchmark REPORT_FILE WORK_DIR");
        }
        List<Row> rows = run(Path.of(args[1]));
        String report = render(rows);
        Files.writeString(Path.of(args[0]), report);
        System.out.print(report);
    }

    static List<Row> run(Path data) throws Exception {
        Files.createDirectories(data);
        List<Path> files;
        try (Stream<Path> listing = Files.list(DEV)) {
            files = listing.filter(p -> p.toString().endsWith(".eml")).sorted().toList();
        }
        ObjectMapper json = new ObjectMapper();
        MimeEmailParser parser = new MimeEmailParser();
        // Phase 1: the same emails, thread ids included, in another project.
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(data.resolve("index"))) {
            PrototypeApp.Wiring other = wiring(indexes, data, "other");
            for (Path file : files) {
                Email email = parser.parse(Files.readAllBytes(file), "other");
                other.coordinator.run(other.coordinator.receive(email), PrototypeApp.BLACKBOARD_LIMITS);
            }
        }
        // Phase 2: each email of the real project in a fresh wiring, as after a restart.
        List<Row> rows = new ArrayList<>();
        List<String[]> seen = new ArrayList<>(); // {message id, gold thread}
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".eml", "");
            String thread = json.readTree(file.resolveSibling(name + ".gold.json").toFile()).path("threadId").asText();
            Email email = parser.parse(Files.readAllBytes(file), "uni");
            Set<String> expected = new LinkedHashSet<>();
            for (String[] earlier : seen) {
                if (earlier[1].equals(thread)) {
                    expected.add(earlier[0]);
                }
            }
            Set<String> found = new LinkedHashSet<>();
            Set<String> otherThread = new LinkedHashSet<>();
            int otherProject = 0;
            try (ProjectIndexes indexes = ProjectIndexes.onDisk(data.resolve("index"))) {
                PrototypeApp.Wiring uni = wiring(indexes, data, "uni");
                BlackboardSnapshot state = uni.coordinator.run(uni.coordinator.receive(email), PrototypeApp.BLACKBOARD_LIMITS);
                for (Evidence item : state.evidence()) {
                    if (item.provenance() == null || item.provenance().description() == null
                            || !item.provenance().description().startsWith(THREAD_MESSAGE)) {
                        continue;
                    }
                    // Each agent's copy of a message carries a suffix after '#' (its topic or role); the message is the part before.
                    String message = item.id().contains("#") ? item.id().substring(0, item.id().indexOf('#')) : item.id();
                    if (item.scope() != null && !"uni".equals(item.scope().projectId())) {
                        otherProject++;
                    } else if (message.equals(email.id())) {
                        continue; // the email itself, recorded on receipt
                    } else if (expected.contains(message)) {
                        found.add(message);
                    } else {
                        otherThread.add(message);
                    }
                }
            }
            rows.add(new Row(name, expected, found, otherThread, otherProject));
            seen.add(new String[] {email.id(), thread});
        }
        return rows;
    }

    private static PrototypeApp.Wiring wiring(ProjectIndexes indexes, Path data, String project) {
        return PrototypeApp.Wiring.create(indexes, new PrototypeApp.Options(null, KNOWLEDGE, data, project,
                PrototypeApp.Architecture.BLACKBOARD, AgendaStrategy.Settings.defaults().minContribution(), LlmSettings.template()));
    }

    static String render(List<Row> rows) {
        int expected = rows.stream().mapToInt(r -> r.expected().size()).sum();
        int found = rows.stream().mapToInt(r -> r.found().size()).sum();
        int otherThread = rows.stream().mapToInt(r -> r.otherThread().size()).sum();
        int otherProject = rows.stream().mapToInt(Row::otherProject).sum();
        long continuing = rows.stream().filter(r -> !r.expected().isEmpty()).count();
        StringBuilder out = new StringBuilder("# Conversation Memory After a Restart (benchmark plan C14)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z. The 24 `synthetic-dev` emails, first all under ")
                .append("a second project with the same thread identifiers, then each under the real project in a freshly created ")
                .append("wiring on the same data folder (a restart before every email). Template generation.\n\n")
                .append("| Measure | Value |\n| --- | --- |\n")
                .append("| Emails that continue a thread | ").append(continuing).append(" |\n")
                .append("| Earlier thread messages found after the restart (recall) | ").append(found).append(" / ").append(expected).append(" |\n")
                .append("| Thread messages from other threads of the same project | ").append(otherThread).append(" |\n")
                .append("| Thread messages from the other project (leaks) | ").append(otherProject).append(" |\n\n")
                .append("| Email | Earlier messages of its thread | Found | Missed | From other threads | From the other project |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        for (Row r : rows) {
            if (r.expected().isEmpty() && r.otherThread().isEmpty() && r.otherProject() == 0) {
                continue;
            }
            Set<String> missed = new LinkedHashSet<>(r.expected());
            missed.removeAll(r.found());
            out.append("| ").append(r.email()).append(" | ").append(r.expected().size()).append(" | ").append(r.found().size())
                    .append(" | ").append(String.join(", ", missed)).append(" | ").append(String.join(", ", r.otherThread()))
                    .append(" | ").append(r.otherProject()).append(" |\n");
        }
        return out.toString();
    }
}
