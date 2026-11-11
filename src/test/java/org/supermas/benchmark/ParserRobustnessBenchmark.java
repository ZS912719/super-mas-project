package org.supermas.benchmark;

import org.supermas.metadata.EmailParseException;
import org.supermas.metadata.LanguageDetector;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.metadata.QuoteSignatureStripper;
import org.supermas.model.Domain.Email;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Benchmark plan C01: does the MIME parser, followed by the stripper and the language detector, survive real mail?
 * Every file of the Enron maildir is parsed; failures and missing fields are counted, and the time per message is
 * measured. Writes counts only, never content, addresses or folder names.
 *
 * <p>Usage: {@code ParserRobustnessBenchmark MAILDIR REPORT_FILE}
 */
public final class ParserRobustnessBenchmark {
    private ParserRobustnessBenchmark() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ParserRobustnessBenchmark MAILDIR REPORT_FILE");
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(Path.of(args[0]))) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        MimeEmailParser parser = new MimeEmailParser();
        QuoteSignatureStripper stripper = new QuoteSignatureStripper();
        LanguageDetector languages = new LanguageDetector();
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Integer> language = new TreeMap<>();
        long[] parseNanos = new long[files.size()];
        long[] pipelineNanos = new long[files.size()];
        int measured = 0;
        long started = System.nanoTime();
        for (Path file : files) {
            byte[] raw = Files.readAllBytes(file);
            long t0 = System.nanoTime();
            Email email;
            try {
                email = parser.parse(raw, "enron");
            } catch (EmailParseException | RuntimeException e) {
                counts.merge("parse failed", 1, Integer::sum);
                continue;
            }
            long t1 = System.nanoTime();
            String text;
            try {
                text = stripper.currentText(email.body() == null ? "" : email.body());
                language.merge(languages.detect(text), 1, Integer::sum);
            } catch (RuntimeException e) {
                counts.merge("stripper or language detector failed", 1, Integer::sum);
                continue;
            }
            long t2 = System.nanoTime();
            parseNanos[measured] = t1 - t0;
            pipelineNanos[measured] = t2 - t0;
            measured++;
            counts.merge("parsed", 1, Integer::sum);
            count(counts, "no Message-ID (synthetic id used)", email.id() == null || email.id().startsWith("<synthetic"));
            count(counts, "no sender address", email.sender() == null || email.sender().isBlank());
            count(counts, "no recipients", email.recipients() == null || email.recipients().isEmpty());
            count(counts, "no date", email.receivedAt() == null);
            count(counts, "empty body", email.body() == null || email.body().isBlank());
            count(counts, "empty current text after stripping", text.isBlank());
            count(counts, "thread id differs from message id (a reply)", !email.scope().threadId().equals(email.id()));
        }
        double seconds = (System.nanoTime() - started) / 1e9;
        long[] parse = Arrays.copyOf(parseNanos, measured);
        long[] pipeline = Arrays.copyOf(pipelineNanos, measured);
        Arrays.sort(parse);
        Arrays.sort(pipeline);
        StringBuilder out = new StringBuilder("# Parser Robustness on Enron (benchmark plan C01)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z. Every file of the Enron maildir (")
                .append(files.size()).append(" messages) through `MimeEmailParser` (permissive), `QuoteSignatureStripper` and ")
                .append("`LanguageDetector`. Counts only.\n\n| Measure | Messages | Share |\n| --- | --- | --- |\n");
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" | ")
                    .append(String.format(Locale.ROOT, "%.4f", (double) e.getValue() / files.size())).append(" |\n");
        }
        out.append("\n| Detected language of the current text | Messages |\n| --- | --- |\n");
        language.forEach((lang, n) -> out.append("| ").append(lang).append(" | ").append(n).append(" |\n"));
        out.append("\n| Time per message | p50 | p95 | p99 |\n| --- | --- | --- | --- |\n")
                .append("| Parse | ").append(ms(parse, 0.5)).append(" | ").append(ms(parse, 0.95)).append(" | ").append(ms(parse, 0.99)).append(" |\n")
                .append("| Parse + strip + language | ").append(ms(pipeline, 0.5)).append(" | ").append(ms(pipeline, 0.95)).append(" | ")
                .append(ms(pipeline, 0.99)).append(" |\n\n")
                .append(String.format(Locale.ROOT, "Wall time %.0f s, including reading the files.%n", seconds));
        Files.writeString(Path.of(args[1]), out.toString());
        System.out.print(out);
    }

    private static void count(Map<String, Integer> counts, String name, boolean condition) {
        counts.merge(name, condition ? 1 : 0, Integer::sum);
    }

    private static String ms(long[] sorted, double quantile) {
        if (sorted.length == 0) {
            return "n/a";
        }
        int index = Math.min(sorted.length - 1, (int) Math.ceil(quantile * sorted.length) - 1);
        return String.format(Locale.ROOT, "%.3f ms", sorted[Math.max(0, index)] / 1e6);
    }
}
