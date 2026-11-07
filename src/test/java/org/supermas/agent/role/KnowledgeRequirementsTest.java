package org.supermas.agent.role;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;

/** Requirements stated in the knowledge that the email does not meet become missing information (to-do 3.9). */
class KnowledgeRequirementsTest {
    private static Evidence knowledge(String source, String file) throws Exception {
        return new Evidence("kb:" + file, new Scope("uni", "t"), EvidenceKind.RETRIEVED_EVIDENCE,
                new Provenance(source, "From knowledge source " + source, Instant.EPOCH),
                Files.readString(Path.of("src/test/resources/university/knowledge", file)), Set.of("student"));
    }

    @Test
    void theIllStudentIsAskedForTheCertificateButNotForTheCourseSheNamed() throws Exception {
        List<String> unmet = KnowledgeRequirements.unmet(List.of(knowledge("student-faq", "student-faq/extensions.md")),
                "I have been ill this week. Could I have an extension for the CS204 project?", Set.of("COURSE", "ID"));
        assertEquals(List.of("medical certificate (required by student-faq)"), unmet);
    }

    @Test
    void theStudentNumberIsMissingOnlyWithoutAnId() throws Exception {
        Evidence certificates = knowledge("admin-procedures", "admin-procedures/certificates.md");
        assertEquals(List.of("ID"), KnowledgeRequirements.unmet(List.of(certificates),
                "Could the office send me an attestation of enrolment for my visa?", Set.of()));
        assertEquals(List.of(), KnowledgeRequirements.unmet(List.of(certificates), "My number is 20231187.", Set.of("ID")));
    }

    @Test
    void aCertificateTheEmailMentionsIsMetAndThreadMessagesAreNotRules() throws Exception {
        assertEquals(List.of(), KnowledgeRequirements.unmet(List.of(knowledge("student-faq", "student-faq/extensions.md")),
                "I attach my medical certificate. Could I have an extension for CS204?", Set.of("COURSE")));
        Evidence message = new Evidence("<m@x>", new Scope("uni", "t"), EvidenceKind.RETRIEVED_EVIDENCE,
                new Provenance("mail", "Thread message of kind RECEIVED", Instant.EPOCH), "You must include your passport.", Set.of());
        assertEquals(List.of(), KnowledgeRequirements.unmet(List.of(message), "Hello", Set.of()));
        // "must be reported" is an obligation of staff, not something the sender has to provide.
        assertEquals(List.of(), KnowledgeRequirements.unmet(List.of(knowledge("teaching-guide", "teaching-guide/marking.md")),
                "Hello", Set.of()));
    }

    @Test
    void aMissingCourseIsTheCourseFieldSoThePlanListsItOnce() throws Exception {
        assertEquals(List.of("COURSE", "medical certificate (required by student-faq)"), KnowledgeRequirements.unmet(
                List.of(knowledge("student-faq", "student-faq/extensions.md")), "Could I have an extension for the assignment due Monday?",
                Set.of()));
    }
}
