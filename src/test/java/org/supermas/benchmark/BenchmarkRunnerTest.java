package org.supermas.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkRunnerTest {
    private static final String GOLD_JSON = """
            {"sender":"a@example.edu","recipients":["b@example.edu"],"date":"2026-11-03T08:12:00Z",
             "threadId":"<1@example.edu>","currentText":"Hi","entities":[],"speechActs":[],
             "roleHint":null,"language":"%s"}
            """;

    @TempDir
    Path directory;

    @BeforeEach
    void writeTwoEmails() throws IOException {
        Files.writeString(directory.resolve("en-1.eml"), "From: a@example.edu\r\n\r\nHi\r\n");
        Files.writeString(directory.resolve("en-1.gold.json"), GOLD_JSON.formatted("en"));
        Files.writeString(directory.resolve("fr-1.eml"), "From: a@example.edu\r\n\r\nSalut\r\n");
        Files.writeString(directory.resolve("fr-1.gold.json"), GOLD_JSON.formatted("fr"));
    }

    @Test
    void emptyPredictorScoresZeroAndReportsEachLanguage() throws IOException {
        var result = BenchmarkRunner.run("empty", Dataset.load(directory), rawEmail -> Extraction.empty());
        String report = BenchmarkRunner.report(result, "fixture");

        assertEquals(0, result.failures());
        assertTrue(report.contains("Macro F1: 0.000"));
        assertTrue(report.contains("## Language: en (1 emails)"));
        assertTrue(report.contains("## Language: fr (1 emails)"));
    }

    @Test
    void oraclePredictorScoresOne() throws IOException {
        Dataset dataset = Dataset.load(directory);
        Extraction gold = dataset.cases().get(0).gold();
        var result = BenchmarkRunner.run("oracle", dataset, rawEmail -> gold);
        assertEquals(1.0, Scorer.score(result.pairs().subList(0, 1)).get("sender").f1());
    }

    @Test
    void predictorFailureIsCountedAsEmptyPrediction() throws IOException {
        var result = BenchmarkRunner.run("broken", Dataset.load(directory), rawEmail -> {
            throw new IOException("model offline");
        });
        assertEquals(2, result.failures());
        assertEquals(Extraction.empty(), result.pairs().get(0).predicted());
    }

    @Test
    void emailWithoutLabelsIsRejected() throws IOException {
        Files.delete(directory.resolve("fr-1.gold.json"));
        assertThrows(IOException.class, () -> Dataset.load(directory));
    }

    @Test
    void changedDataChangesTheDatasetHash() throws IOException {
        String before = Dataset.load(directory).sha256();
        Files.writeString(directory.resolve("fr-1.eml"), "From: changed@example.edu\r\n\r\nSalut\r\n");
        assertTrue(!before.equals(Dataset.load(directory).sha256()));
    }

    @Test
    void percentileUsesNearestRank() {
        List<Long> values = List.of(40L, 10L, 30L, 20L);
        assertEquals(20L, BenchmarkRunner.percentile(values, 50));
        assertEquals(40L, BenchmarkRunner.percentile(values, 95));
    }
}
