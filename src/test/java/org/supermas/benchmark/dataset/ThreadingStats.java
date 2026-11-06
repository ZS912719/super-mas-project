package org.supermas.benchmark.dataset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.supermas.metadata.EmailThreader;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.model.Domain.Email;

/**
 * Prints threading statistics for a folder of raw messages, with no message contents, so the
 * subject fallback's effect can be measured on real mail without disclosing it.
 *
 * <p>Usage: ThreadingStats INPUT_DIR
 */
public final class ThreadingStats {
    private ThreadingStats() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ThreadingStats INPUT_DIR");
            System.exit(2);
        }
        MimeEmailParser parser = new MimeEmailParser();
        List<Email> emails = new ArrayList<>();
        int unparseable = 0;
        try (Stream<Path> walk = Files.walk(Path.of(args[0]))) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                try {
                    emails.add(parser.parse(Files.readAllBytes(file), "enron"));
                } catch (Exception e) {
                    unparseable++;
                }
            }
        }
        int references = distinctThreads(new EmailThreader(false).assignThreadIds(emails));
        int withFallback = distinctThreads(new EmailThreader(true).assignThreadIds(emails));
        System.out.printf("messages=%d unparseable=%d%n", emails.size(), unparseable);
        System.out.printf("threads (references only)=%d%n", references);
        System.out.printf("threads (with subject fallback)=%d%n", withFallback);
        System.out.printf("threads merged by subject fallback=%d%n", references - withFallback);
    }

    private static int distinctThreads(Map<String, String> threadIdByMessageId) {
        return new HashMap<>(threadIdByMessageId).values().stream().distinct().toList().size();
    }
}
