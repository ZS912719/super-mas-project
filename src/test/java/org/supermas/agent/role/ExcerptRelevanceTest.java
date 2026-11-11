package org.supermas.agent.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.ContextSelection;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;

/** Excerpts that share nothing with the requests are not passed on (step 13, run 3 and runs A/B). */
class ExcerptRelevanceTest {
    private static final Path KNOWLEDGE = Path.of("src/test/resources/university/knowledge");

    private static Evidence document(String id, String file, String description) throws Exception {
        return new Evidence(id, new Scope("uni", "t"), EvidenceKind.RETRIEVED_EVIDENCE,
                new Provenance("student-faq", description, Instant.EPOCH), Files.readString(KNOWLEDGE.resolve(file)), Set.of("student"));
    }

    private static List<String> ids(ContextSelection selection) {
        return selection.evidence().stream().map(Evidence::id).toList();
    }

    @Test
    void theExtensionRequestKeepsThePolicyAndDropsRoomsAndResits() throws Exception {
        ContextSelection all = new ContextSelection(List.of(document("extensions", "student-faq/extensions.md", "From knowledge source student-faq"),
                document("contacts", "general-info/contacts.md", "From knowledge source general-info"),
                document("exams", "student-faq/exams.md", "From knowledge source student-faq")), 0, List.of());
        List<String> notes = new ArrayList<>();

        ContextSelection kept = ExcerptRelevance.related(all, List.of("Could I have an extension for the CS204 project?",
                "Extension for CS204 project"), notes);

        assertEquals(List.of("extensions"), ids(kept));
        assertEquals(List.of("2 excerpts unrelated to the requests were left out"), notes);
    }

    @Test
    void roomsGrantsAndEnrolmentMatchTheirDocuments() throws Exception {
        ContextSelection all = new ContextSelection(List.of(document("contacts", "general-info/contacts.md", "x"),
                document("grants", "research-office/grants.md", "x"),
                document("certificates", "admin-procedures/certificates.md", "x"),
                document("marking", "teaching-guide/marking.md", "x")), 0, List.of());
        // The staff marking guide also mentions room booking; who may read it is decided by its readers, not here.
        assertEquals(List.of("contacts", "marking"), ids(ExcerptRelevance.related(all,
                List.of("Could we swap lecture rooms next Tuesday?"), new ArrayList<>())));
        // "Late extensions granted" stems to "grant" too: one shared stem is lenient on purpose.
        assertEquals(List.of("grants", "marking"), ids(ExcerptRelevance.related(all,
                List.of("Would you co-author the grant proposal with me before the funding deadline?"), new ArrayList<>())));
        assertEquals(List.of("certificates"), ids(ExcerptRelevance.related(all,
                List.of("Could the office send me an attestation of enrolment for my visa?"), new ArrayList<>())));
    }

    @Test
    void ownThreadMessagesAndOtherLanguagesAreKept() throws Exception {
        Evidence thread = new Evidence("<earlier@x>", new Scope("uni", "t"), EvidenceKind.RETRIEVED_EVIDENCE,
                new Provenance("mail", "Thread message of kind RECEIVED", Instant.EPOCH), "Lunch next week?", Set.of("student"));
        Evidence english = document("extensions", "student-faq/extensions.md", "x");
        ContextSelection all = new ContextSelection(List.of(thread, english), 0, List.of());
        assertEquals(List.of("<earlier@x>"), ids(ExcerptRelevance.related(all, List.of("Is the grant proposal ready?"), new ArrayList<>())));
        ContextSelection french = ExcerptRelevance.related(all, List.of("Serait-il possible de reporter le rendu de Bases de données ?"),
                new ArrayList<>());
        assertTrue(ids(french).contains("extensions"), "English knowledge is kept for a French request");
    }

    @Test
    void requestsWithoutMeaningfulWordsKeepOnlyTheThread() throws Exception {
        Evidence thread = new Evidence("<earlier@x>", new Scope("uni", "t"), EvidenceKind.RETRIEVED_EVIDENCE,
                new Provenance("mail", "Thread message of kind RECEIVED", Instant.EPOCH), "Lunch next week?", Set.of("student"));
        Evidence english = document("extensions", "student-faq/extensions.md", "x");
        ContextSelection all = new ContextSelection(List.of(thread, english), 0, List.of());
        List<String> notes = new ArrayList<>();
        assertEquals(List.of("<earlier@x>"), ids(ExcerptRelevance.related(all, List.of("9", ""), notes)));
        assertEquals(List.of("1 excerpts unrelated to the requests were left out"), notes);
        assertEquals(List.of(), ids(ExcerptRelevance.related(new ContextSelection(List.of(english), 0, List.of()),
                List.of("Could you please?"), new ArrayList<>())), "asking words alone are not a subject");
    }
}
