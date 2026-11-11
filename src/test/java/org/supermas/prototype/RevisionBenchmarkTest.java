package org.supermas.prototype;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.llm.LlmSettings;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisionBenchmarkTest {
    @Test
    void everyReplyExpectedEmailIsRejectedOnceAndRevised(@TempDir Path work) throws Exception {
        List<RevisionBenchmark.Row> rows = RevisionBenchmark.run("multi", Path.of("benchmark/data/synthetic-multi"), work,
                LlmSettings.template());
        assertEquals(12, rows.size());
        assertTrue(rows.stream().allMatch(RevisionBenchmark.Row::v2Drafted), rows.toString());
        // The template ignores feedback, so nothing is applied: the floor of this benchmark.
        assertTrue(rows.stream().noneMatch(RevisionBenchmark.Row::applied));
        assertTrue(RevisionBenchmark.render(rows, LlmSettings.template()).contains("| fact |"));
    }
}
