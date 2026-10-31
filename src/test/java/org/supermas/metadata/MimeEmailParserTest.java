package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.supermas.benchmark.Dataset;
import org.supermas.model.Domain.Email;

class MimeEmailParserTest {
    private final MimeEmailParser parser = new MimeEmailParser();

    private static byte[] raw(String text) {
        return text.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }

    @Test
    void extractsFactsForEverySyntheticEmail() throws Exception {
        Dataset dataset = Dataset.load(Path.of("benchmark/data/synthetic-dev"));
        for (Dataset.Case testCase : dataset.cases()) {
            Email email = parser.parse(testCase.rawEmail(), "project-1");
            String id = testCase.id();
            assertEquals(testCase.gold().sender(), email.sender(), id + " sender");
            assertEquals(testCase.gold().recipients(), email.recipients(), id + " recipients");
            assertEquals(testCase.gold().date(), email.receivedAt().toString(), id + " date");
            assertFalse(email.subject().isBlank(), id + " subject");
            assertFalse(email.subject().contains("=?"), id + " subject must be decoded");
            assertTrue(normalize(email.body()).contains(testCase.gold().currentText()),
                    id + " body must contain the new text");
        }
    }

    @Test
    void decodesEncodedSubjectAndQuotedPrintableBody() throws Exception {
        Dataset dataset = Dataset.load(Path.of("benchmark/data/synthetic-dev"));
        Email french = parse(dataset, "fr-01");
        assertTrue(french.subject().contains("Bases de données"), "decoded subject");
        assertTrue(normalize(french.body()).contains("J'ai été malade"), "decoded accented body");
    }

    @Test
    void prefersPlainTextOverHtmlInMultipart() throws Exception {
        Dataset dataset = Dataset.load(Path.of("benchmark/data/synthetic-dev"));
        Email email = parse(dataset, "en-09");
        assertTrue(email.body().contains("Introduction to Programming"), "plain text body");
        assertFalse(email.body().contains("<p>"), "HTML markup must not leak in");
    }

    @Test
    void usesFirstReferenceAsProvisionalThreadId() throws Exception {
        Email email = parser.parse(raw("""
                From: a@example.edu
                To: b@example.edu
                Subject: Re: hi
                Message-ID: <reply@example.edu>
                References: <root@example.edu> <mid@example.edu>
                Date: Mon, 02 Nov 2026 09:00:00 +0000

                text
                """), "project-1");
        assertEquals("<reply@example.edu>", email.id());
        assertEquals("<root@example.edu>", email.scope().threadId());
        assertEquals("project-1", email.scope().projectId());
    }

    @Test
    void fallsBackToOwnIdWhenThereAreNoReferences() throws Exception {
        Email email = parser.parse(raw("""
                From: a@example.edu
                To: b@example.edu
                Subject: hi
                Message-ID: <solo@example.edu>
                Date: Mon, 02 Nov 2026 09:00:00 +0000

                text
                """), "project-1");
        assertEquals("<solo@example.edu>", email.scope().threadId());
    }

    @Test
    void synthesizesThreadIdAndIdWhenMessageIdIsMissing() throws Exception {
        Email email = parser.parse(raw("""
                From: a@example.edu
                To: b@example.edu
                Subject: hi
                Date: Mon, 02 Nov 2026 09:00:00 +0000

                text
                """), "project-1");
        assertTrue(email.id().startsWith("<synthetic-"), "synthetic id");
        assertEquals(email.id(), email.scope().threadId(), "thread falls back to the synthetic id");
    }

    private Email parse(Dataset dataset, String caseId) throws Exception {
        Dataset.Case testCase = dataset.cases().stream()
                .filter(c -> c.id().equals(caseId)).findFirst().orElseThrow();
        return parser.parse(testCase.rawEmail(), "project-1");
    }
}
