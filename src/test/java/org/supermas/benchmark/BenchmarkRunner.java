package org.supermas.benchmark;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Runs one predictor over a dataset and writes a Markdown report.
 * Usage: BenchmarkRunner DATA_DIRECTORY REPORT_FILE PREDICTOR_NAME
 */
public final class BenchmarkRunner {
    /** Suppliers, so that a variant's models are loaded only when that variant runs. */
    private static final Map<String, Supplier<Predictor>> PREDICTORS = Map.of(
            "empty", () -> rawEmail -> Extraction.empty(),
            "facts", FactsPredictor::new,
            "entity-rules", EntityPredictor::rulesOnly,
            "entity-gliner", () -> EntityPredictor.gliner(false),
            "entity-gliner-rules", () -> EntityPredictor.gliner(true),
            "entity-hybrid", EntityPredictor::hybrid,
            "labels-rules", LabelPredictor::rules,
            "metadata-rules", MetadataPredictor::rules,
            "metadata-entity-model", () -> MetadataPredictor.create(true, false),
            "metadata-all-models", () -> MetadataPredictor.create(true, true));

    private BenchmarkRunner() { }

    public record Result(String variant, Dataset dataset, List<Scorer.Pair> pairs, int failures,
                         List<Long> latenciesNanos) { }

    public static void main(String[] args) throws IOException {
        if (args.length != 3 || !PREDICTORS.containsKey(args[2])) {
            System.err.println("Usage: BenchmarkRunner DATA_DIRECTORY REPORT_FILE PREDICTOR_NAME");
            System.err.println("Predictors: " + PREDICTORS.keySet());
            System.exit(2);
        }
        Result result = run(args[2], Dataset.load(Path.of(args[0])), PREDICTORS.get(args[2]).get());
        Path reportFile = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, report(result, args[0]));
        System.out.println("Report written to " + reportFile);
    }

    /** A predictor failure counts as an empty prediction, so it lowers scores and stays visible. */
    public static Result run(String variant, Dataset dataset, Predictor predictor) {
        List<Scorer.Pair> pairs = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        int failures = 0;
        for (Dataset.Case testCase : dataset.cases()) {
            Extraction predicted;
            long start = System.nanoTime();
            try {
                predicted = predictor.predict(testCase.rawEmail().clone());
            } catch (Exception e) {
                predicted = Extraction.empty();
                failures++;
            }
            latencies.add(System.nanoTime() - start);
            pairs.add(new Scorer.Pair(testCase.gold(), predicted));
        }
        return new Result(variant, dataset, pairs, failures, latencies);
    }

    public static String report(Result result, String dataLabel) {
        StringBuilder out = new StringBuilder();
        out.append("# Benchmark Report: ").append(result.variant()).append("\n\n");
        out.append("| Item | Value |\n| --- | --- |\n");
        out.append(row("Dataset", dataLabel));
        out.append(row("Dataset SHA-256", result.dataset().sha256()));
        out.append(row("Emails", Integer.toString(result.pairs().size())));
        out.append(row("Predictor failures", Integer.toString(result.failures())));
        out.append(row("Latency p50", milliseconds(percentile(result.latenciesNanos(), 50))));
        out.append(row("Latency p95", milliseconds(percentile(result.latenciesNanos(), 95))));
        out.append(row("Peak heap", peakHeapMegabytes() + " MB"));
        out.append(row("Machine", System.getProperty("os.name") + ", "
                + Runtime.getRuntime().availableProcessors() + " logical CPUs"));
        out.append(row("Java", System.getProperty("java.version")));
        out.append('\n');
        section(out, "All emails", result.pairs());
        Map<String, List<Scorer.Pair>> byLanguage = new TreeMap<>();
        for (Scorer.Pair pair : result.pairs()) {
            byLanguage.computeIfAbsent(String.valueOf(pair.gold().language()), key -> new ArrayList<>())
                    .add(pair);
        }
        byLanguage.forEach((language, pairs) -> section(out, "Language: " + language, pairs));
        return out.toString();
    }

    /** Nearest-rank percentile. */
    static long percentile(List<Long> values, int percentile) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }

    private static void section(StringBuilder out, String title, List<Scorer.Pair> pairs) {
        Map<String, Scorer.FieldScore> scores = Scorer.score(pairs);
        out.append("## ").append(title).append(" (").append(pairs.size()).append(" emails)\n\n");
        out.append("Macro F1: ").append(number(Scorer.macroF1(scores))).append("\n\n");
        out.append("| Field | Gold | TP | FP | FN | Precision | Recall | F1 | Null accuracy |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        scores.forEach((field, s) -> out.append("| ")
                .append(String.join(" | ", Arrays.asList(field, Integer.toString(s.goldCount()),
                        Integer.toString(s.truePositives()), Integer.toString(s.falsePositives()),
                        Integer.toString(s.falseNegatives()), number(s.precision()), number(s.recall()),
                        number(s.f1()), number(s.nullAccuracy()))))
                .append(" |\n"));
        out.append('\n');
    }

    private static String row(String name, String value) {
        return "| " + name + " | " + value + " |\n";
    }

    private static String number(Double value) {
        return value == null ? "n/a" : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String milliseconds(long nanos) {
        return String.format(Locale.ROOT, "%.3f ms", nanos / 1_000_000.0);
    }

    private static long peakHeapMegabytes() {
        long bytes = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                bytes += pool.getPeakUsage().getUsed();
            }
        }
        return bytes / (1024 * 1024);
    }
}
