package org.supermas.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reply checks (step 13) may sit beside their emails without changing the dataset or its fingerprint. */
class DatasetReplyChecksTest {
    @Test
    void replyChecksAreAllowedAndLeftOutOfTheFingerprint(@TempDir Path folder) throws IOException {
        Path source = Path.of("benchmark/data/synthetic-dev");
        for (String file : new String[] {"en-01.eml", "en-01.gold.json", "en-02.eml", "en-02.gold.json"}) {
            Files.copy(source.resolve(file), folder.resolve(file));
        }
        Dataset without = Dataset.load(folder);
        Files.copy(source.resolve("en-01.reply.json"), folder.resolve("en-01.reply.json"));
        Dataset with = Dataset.load(folder);
        assertEquals(without.sha256(), with.sha256());
        assertEquals(2, with.cases().size());

        Files.writeString(folder.resolve("en-99.reply.json"), "{}");
        assertThrows(IOException.class, () -> Dataset.load(folder), "a reply check needs its email");
    }
}
