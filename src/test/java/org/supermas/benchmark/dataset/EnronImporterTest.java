package org.supermas.benchmark.dataset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.benchmark.Dataset;
import org.supermas.benchmark.Extraction;

class EnronImporterTest {
    @TempDir
    Path input;
    @TempDir
    Path output;

    private static byte[] message(String id, String from, String to, String cc, String date,
                                  String references, String body) {
        StringBuilder head = new StringBuilder();
        if (id != null) {
            head.append("Message-ID: ").append(id).append("\r\n");
        }
        head.append("From: ").append(from).append("\r\n");
        head.append("To: ").append(to).append("\r\n");
        if (cc != null) {
            head.append("Cc: ").append(cc).append("\r\n");
        }
        head.append("Subject: test\r\n");
        head.append("Date: ").append(date).append("\r\n");
        if (references != null) {
            head.append("References: ").append(references).append("\r\n");
        }
        head.append("\r\n").append(body).append("\r\n");
        return head.toString().getBytes(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void writeCorpus() throws IOException {
        Path folderA = Files.createDirectories(input.resolve("alice/sent"));
        Path folderB = Files.createDirectories(input.resolve("bob/inbox"));
        // Thread a: two linked messages, written out of date order.
        Files.write(folderA.resolve("1."), message("<a2@enron.com>", "Alice <alice@enron.com>",
                "bob@enron.com", null, "Mon, 02 Nov 2026 10:00:00 +0100", "<a1@enron.com>", "reply"));
        Files.write(folderB.resolve("1."), message("<a1@enron.com>", "Bob <bob@enron.com>",
                "Alice@enron.com", "carol@enron.com", "Mon, 02 Nov 2026 09:00:00 +0100", null, "start"));
        // A standalone message, duplicated across two folders.
        byte[] standalone = message("<b1@enron.com>", "Carol <carol@enron.com>", "alice@enron.com",
                null, "Tue, 03 Nov 2026 08:00:00 +0000", null, "notice");
        Files.write(folderA.resolve("2."), standalone);
        Files.write(folderB.resolve("2."), standalone);
        // A message with no Message-ID, which gets a synthetic one.
        Files.write(folderB.resolve("3."), message(null, "dan@enron.com", "alice@enron.com", null,
                "Wed, 04 Nov 2026 08:00:00 +0000", null, "no id"));
    }

    @Test
    void deduplicatesGroupsThreadsAndKeepsThreadWhole() throws IOException {
        var result = EnronImporter.select(EnronImporter.readAll(input), 0, 1L);
        assertEquals(5, result.messagesRead());
        assertEquals(1, result.duplicatesDropped());
        assertEquals(3, result.threadsFound());
        assertEquals(4, result.messagesWritten());

        var thread = result.selected().stream()
                .filter(group -> group.messages().size() == 2).findFirst().orElseThrow();
        assertEquals("<a1@enron.com>", thread.threadId());
        assertEquals("<a1@enron.com>", thread.messages().get(0).messageId());
    }

    @Test
    void derivesFactsWithTheIndependentParser() throws IOException {
        var result = EnronImporter.select(EnronImporter.readAll(input), 0, 1L);
        ParsedMessage root = result.selected().stream()
                .flatMap(group -> group.messages().stream())
                .filter(message -> message.messageId().equals("<a1@enron.com>")).findFirst().orElseThrow();
        assertEquals("bob@enron.com", root.sender());
        assertEquals(List.of("alice@enron.com", "carol@enron.com"), root.recipients());
        assertEquals("2026-11-02T08:00:00Z", root.sentDate().toString());
    }

    @Test
    void synthesizesMissingMessageId() throws IOException {
        var result = EnronImporter.select(EnronImporter.readAll(input), 0, 1L);
        ParsedMessage synthetic = result.selected().stream()
                .flatMap(group -> group.messages().stream())
                .filter(ParsedMessage::syntheticId).findFirst().orElseThrow();
        assertTrue(synthetic.messageId().startsWith("<synthetic-"));
        assertTrue(synthetic.warnings().stream().anyMatch(w -> w.contains("Message-ID")));
    }

    @Test
    void samplingIsDeterministicForASeed() throws IOException {
        List<ParsedMessage> messages = EnronImporter.readAll(input);
        var first = EnronImporter.select(messages, 2, 42L);
        var second = EnronImporter.select(messages, 2, 42L);
        assertEquals(2, first.selected().size());
        assertEquals(threadIds(first), threadIds(second));
    }

    @Test
    void writesEmlUnchangedAndTemplatesThatCannotLoadAsGold() throws IOException {
        var result = EnronImporter.select(EnronImporter.readAll(input), 0, 1L);
        // Reach the private writer through main().
        EnronImporter.main(new String[] {input.toString(), output.toString(), "0", "1"});

        Path firstEml = output.resolve("enron-t0001-m01.eml");
        assertTrue(Files.exists(firstEml));
        ParsedMessage expected = result.selected().get(0).messages().get(0);
        assertArrayEquals(expected.raw(), Files.readAllBytes(firstEml));

        String template = Files.readString(output.resolve("enron-t0001-m01.gold.todo.json"));
        assertTrue(template.contains("_status"));
        assertThrows(Exception.class,
                () -> new ObjectMapper().readValue(template, Extraction.class));
    }

    @Test
    void outputFolderCannotBeLoadedAsADatasetUntilReviewed() throws IOException {
        EnronImporter.main(new String[] {input.toString(), output.toString(), "0", "1"});
        assertThrows(IOException.class, () -> Dataset.load(output));
    }

    private static List<String> threadIds(EnronImporter.ImportResult result) {
        return result.selected().stream().map(EnronImporter.ThreadGroup::threadId).sorted().toList();
    }
}
