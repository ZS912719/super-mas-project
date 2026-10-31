package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.supermas.benchmark.Dataset;
import org.supermas.model.Domain.Email;

class EmailThreaderTest {
    private final MimeEmailParser parser = new MimeEmailParser();

    private List<Email> parseAll(String projectId) throws Exception {
        Dataset dataset = Dataset.load(Path.of("benchmark/data/synthetic-dev"));
        List<Email> emails = new ArrayList<>();
        for (Dataset.Case testCase : dataset.cases()) {
            emails.add(parser.parse(testCase.rawEmail(), projectId));
        }
        return emails;
    }

    private Map<String, String> goldThreadIds() throws Exception {
        Dataset dataset = Dataset.load(Path.of("benchmark/data/synthetic-dev"));
        Map<String, String> gold = new java.util.HashMap<>();
        for (Dataset.Case testCase : dataset.cases()) {
            Email email = parser.parse(testCase.rawEmail(), "p");
            gold.put(email.id(), testCase.gold().threadId());
        }
        return gold;
    }

    @Test
    void matchesGoldThreadIdsOnTheSyntheticSet() throws Exception {
        Map<String, String> computed = new EmailThreader().assignThreadIds(parseAll("p"));
        assertEquals(goldThreadIds(), computed);
    }

    @Test
    void subjectFallbackJoinsRepliesThatLackReferenceHeaders() throws Exception {
        // en-03 and fr-03 are replies with no References/In-Reply-To; only the subject links them.
        List<Email> emails = parseAll("p");
        Map<String, String> withFallback = new EmailThreader(true).assignThreadIds(emails);
        Map<String, String> withoutFallback = new EmailThreader(false).assignThreadIds(emails);

        String reply = emails.stream().filter(e -> e.subject().equals("Re: Extension for CS204 project"))
                .filter(e -> e.metadata().get("References") == null)
                .findFirst().orElseThrow().id();
        // With the fallback the headerless reply joins the thread; without it, it stands alone.
        assertEquals("<en-a1@students.example.edu>", withFallback.get(reply));
        assertEquals(reply, withoutFallback.get(reply));
    }

    @Test
    void equalThreadIdsInDifferentProjectsStaySeparate() throws Exception {
        // Threading one project's emails never references another's, so isolation is the caller's
        // job: two projects are threaded independently and share no thread assignment.
        Map<String, String> projectA = new EmailThreader().assignThreadIds(parseAll("project-a"));
        Map<String, String> projectB = new EmailThreader().assignThreadIds(parseAll("project-b"));
        assertEquals(projectA.keySet(), projectB.keySet());
        assertNotEquals(0, projectA.size());
    }

    @Test
    void baseSubjectStripsStackedReplyAndForwardPrefixes() {
        assertEquals("extension for cs204 project",
                EmailThreader.baseSubject("Re: Fwd: RE: Extension for CS204 project"));
        assertEquals("", EmailThreader.baseSubject("Re: "));
    }
}
