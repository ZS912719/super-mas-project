package org.supermas.benchmark.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Turns a folder of raw Enron messages into benchmark material: one `.eml` per message plus a
 * `.gold.todo.json` holding the facts a separate parser could derive, with the model-output fields
 * left empty for a person to fill. See docs/reports/data-import-enron.md.
 *
 * <p>The output is deliberately not a loadable dataset: {@link org.supermas.benchmark.Dataset}
 * pairs `.eml` with `.gold.json`, so the files must be reviewed and renamed before any benchmark
 * can read them. This stops unlabeled data from being scored by accident.
 *
 * <p>Usage: EnronImporter INPUT_DIR OUTPUT_DIR THREAD_LIMIT SEED
 */
public final class EnronImporter {
    private static final String TODO_SUFFIX = ".gold.todo.json";
    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private EnronImporter() { }

    public record ThreadGroup(String threadId, List<ParsedMessage> messages) { }

    public record ImportResult(int messagesRead, int duplicatesDropped, int threadsFound,
                               List<ThreadGroup> selected, List<String> warnings) {
        public int messagesWritten() {
            return selected.stream().mapToInt(group -> group.messages().size()).sum();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("Usage: EnronImporter INPUT_DIR OUTPUT_DIR THREAD_LIMIT SEED");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        int threadLimit = Integer.parseInt(args[2]);
        long seed = Long.parseLong(args[3]);

        List<ParsedMessage> messages = readAll(input);
        ImportResult result = select(messages, threadLimit, seed);
        write(result, output);
        System.out.printf("Read %d messages, dropped %d duplicates, found %d threads,"
                        + " wrote %d messages in %d threads to %s%n",
                result.messagesRead(), result.duplicatesDropped(), result.threadsFound(),
                result.messagesWritten(), result.selected().size(), output.toAbsolutePath());
    }

    /** Reads every file under {@code input} as one message. */
    public static List<ParsedMessage> readAll(Path input) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(input)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        List<ParsedMessage> messages = new ArrayList<>();
        for (Path file : files) {
            messages.add(ParsedMessage.parse(Files.readAllBytes(file)));
        }
        return messages;
    }

    /** Deduplicates, groups into threads, then picks {@code threadLimit} threads using {@code seed}. */
    public static ImportResult select(List<ParsedMessage> messages, int threadLimit, long seed) {
        List<String> warnings = new ArrayList<>();
        int read = messages.size();

        Map<String, ParsedMessage> byId = new LinkedHashMap<>();
        int duplicates = 0;
        for (ParsedMessage message : messages) {
            if (byId.putIfAbsent(message.messageId(), message) != null) {
                duplicates++;
            }
            warnings.addAll(message.warnings());
        }

        Map<String, ThreadGroup> threads = groupIntoThreads(byId);

        List<String> order = new ArrayList<>(threads.keySet());
        Collections.sort(order);
        Collections.shuffle(order, new Random(seed));
        if (threadLimit > 0 && threadLimit < order.size()) {
            order = order.subList(0, threadLimit);
        }
        List<ThreadGroup> selected = new ArrayList<>();
        for (String threadId : order) {
            selected.add(threads.get(threadId));
        }
        selected.sort(Comparator.comparing(group -> earliest(group.messages())));

        return new ImportResult(read, duplicates, threads.size(), selected, warnings);
    }

    /** Groups messages into threads by following Message-ID reference links (RFC 5256 stage one). */
    private static Map<String, ThreadGroup> groupIntoThreads(Map<String, ParsedMessage> byId) {
        Map<String, String> parent = new HashMap<>();
        byId.keySet().forEach(id -> parent.put(id, id));
        for (ParsedMessage message : byId.values()) {
            for (String reference : message.references()) {
                if (byId.containsKey(reference)) {
                    union(parent, message.messageId(), reference);
                }
            }
        }
        Map<String, List<ParsedMessage>> components = new LinkedHashMap<>();
        for (ParsedMessage message : byId.values()) {
            components.computeIfAbsent(find(parent, message.messageId()), key -> new ArrayList<>())
                    .add(message);
        }
        Map<String, ThreadGroup> threads = new LinkedHashMap<>();
        for (List<ParsedMessage> component : components.values()) {
            component.sort(Comparator.comparing(EnronImporter::sortKey));
            // The thread id is the earliest message's id, per the field schema.
            String threadId = component.get(0).messageId();
            threads.put(threadId, new ThreadGroup(threadId, component));
        }
        return threads;
    }

    private static String find(Map<String, String> parent, String node) {
        String root = node;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        String current = node;
        while (!parent.get(current).equals(root)) {
            String next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        parent.put(find(parent, a), find(parent, b));
    }

    private static void write(ImportResult result, Path output) throws IOException {
        Files.createDirectories(output);
        List<Map<String, Object>> manifest = new ArrayList<>();
        int threadIndex = 0;
        for (ThreadGroup group : result.selected()) {
            threadIndex++;
            int messageIndex = 0;
            for (ParsedMessage message : group.messages()) {
                messageIndex++;
                String id = String.format("enron-t%04d-m%02d", threadIndex, messageIndex);
                Files.write(output.resolve(id + ".eml"), message.raw());
                Files.writeString(output.resolve(id + TODO_SUFFIX), template(message, group.threadId()));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("file", id + ".eml");
                entry.put("messageId", message.messageId());
                entry.put("threadId", group.threadId());
                entry.put("syntheticId", message.syntheticId());
                entry.put("warnings", message.warnings());
                manifest.add(entry);
            }
        }
        MAPPER.writeValue(output.resolve("manifest.json").toFile(), manifest);
        Files.writeString(output.resolve("NEEDS-REVIEW.md"), needsReview(result));
    }

    /**
     * A gold template. Facts come from the independent parser; the model-output fields are empty.
     * The `_status` key is intentional: {@code Extraction} rejects unknown fields, so a template
     * cannot be loaded as gold until a reviewer removes it and renames the file to `.gold.json`.
     */
    private static String template(ParsedMessage message, String threadId) {
        Map<String, Object> gold = new LinkedHashMap<>();
        gold.put("_status", "NEEDS_REVIEW: verify facts, fill currentText/entities/speechActs/"
                + "roleHint/language, then remove this key and rename to .gold.json");
        gold.put("sender", message.sender());
        gold.put("recipients", message.recipients());
        gold.put("date", message.sentDate() == null ? null : message.sentDate().toString());
        gold.put("threadId", threadId);
        gold.put("currentText", null);
        gold.put("entities", List.of());
        gold.put("speechActs", List.of());
        gold.put("roleHint", null);
        gold.put("language", "en");
        try {
            return MAPPER.writeValueAsString(gold) + "\n";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String needsReview(ImportResult result) {
        long withWarnings = result.selected().stream()
                .flatMap(group -> group.messages().stream())
                .filter(message -> !message.warnings().isEmpty())
                .count();
        return """
                # Enron import: needs review

                This folder is raw material, not a dataset. Each `.eml` has a `.gold.todo.json`
                template. A benchmark cannot load this folder until every template is reviewed.

                For each message:
                1. Check the facts (sender, recipients, date, threadId) against the email.
                2. Fill `currentText` by removing quotes and signatures (see the field schema).
                3. Add `entities`, `speechActs`, `roleHint` and confirm `language`.
                4. Remove the `_status` line and rename the file to `.gold.json`.

                Messages: %d in %d threads. Messages with parser warnings: %d (see manifest.json).

                These emails are real people's messages. Keep them local and out of Git, and
                respect the privacy of the people named, as the Enron corpus page asks.
                """.formatted(result.messagesWritten(), result.selected().size(), withWarnings);
    }

    private static java.time.Instant earliest(List<ParsedMessage> messages) {
        return messages.stream().map(EnronImporter::sortKey).min(Comparator.naturalOrder())
                .orElse(java.time.Instant.MAX);
    }

    /** Orders messages by date; those without a date sort last but stay in the thread. */
    private static java.time.Instant sortKey(ParsedMessage message) {
        return message.sentDate() == null ? java.time.Instant.MAX : message.sentDate();
    }
}
