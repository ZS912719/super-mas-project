package org.supermas.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.supermas.metadata.EmailThreader;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.model.Domain.Email;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Benchmark plan C02: thread identifiers against the gold {@code threadId}, for the parser alone (reply headers,
 * what the prototype used on receipt until f45bff4), for {@link EmailThreader} with and without its subject fallback, and
 * on receipt with the restricted fallback of {@link org.supermas.memory.SubjectThreadFallback} (the prototype since f45bff4).
 *
 * <p>Usage: {@code ThreadingBenchmark REPORT_FILE DATA_DIR} (a folder of {@code .eml} with metadata gold files)
 */
public final class ThreadingBenchmark {
    private ThreadingBenchmark() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ThreadingBenchmark REPORT_FILE DATA_DIR");
        }
        Path dir = Path.of(args[1]);
        ObjectMapper json = new ObjectMapper();
        MimeEmailParser parser = new MimeEmailParser();
        List<Email> emails = new ArrayList<>();
        Map<String, String> gold = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".eml")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".eml", "");
                Email email = parser.parse(Files.readAllBytes(file), "uni");
                emails.add(email);
                gold.put(email.id(), json.readTree(file.resolveSibling(name + ".gold.json").toFile()).path("threadId").asText());
                names.put(email.id(), name);
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        emails.forEach(e -> headers.put(e.id(), e.scope().threadId()));
        Map<String, Map<String, String>> variants = new LinkedHashMap<>();
        variants.put("Parser alone (reply headers only; the prototype before f45bff4)", headers);
        variants.put("EmailThreader, references only", new EmailThreader(false).assignThreadIds(emails));
        variants.put("EmailThreader with subject fallback", new EmailThreader(true).assignThreadIds(emails));
        variants.put("On receipt with the restricted subject fallback (the prototype since f45bff4)", ReceiptThreading.assign(emails));
        StringBuilder out = new StringBuilder("# Threading (benchmark plan C02)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z on `").append(dir).append("` (")
                .append(emails.size()).append(" emails, gold `threadId`).\n\n| Variant | Thread right | Wrong |\n| --- | --- | --- |\n");
        for (Map.Entry<String, Map<String, String>> v : variants.entrySet()) {
            List<String> wrong = new ArrayList<>();
            for (Email e : emails) {
                if (!gold.get(e.id()).equals(v.getValue().get(e.id()))) {
                    wrong.add(names.get(e.id()));
                }
            }
            out.append("| ").append(v.getKey()).append(" | ").append(emails.size() - wrong.size()).append(" / ")
                    .append(emails.size()).append(" | ").append(String.join(", ", wrong)).append(" |\n");
        }
        Files.writeString(Path.of(args[0]), out.toString());
        System.out.print(out);
    }
}
