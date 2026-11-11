package org.supermas.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.context.ProjectIndexes;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Scope;

/** A reply without reply headers joins its earlier thread only within the same participants and window (B47). */
class SubjectThreadFallbackTest {
    private static final Instant T0 = Instant.parse("2026-11-02T08:14:00Z");
    private static final String STUDENT = "sarah.klein@students.example.edu";
    private static final String OWNER = "e.martin@example.edu";

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();
    private final ConversationRecorder recorder = new ConversationRecorder(new IndexConversationRepository(indexes,
            project -> Optional.of(new ContextSource("mail", project, URI.create("file:///unused"), Set.of("reader")))),
            MessageTagger.none());
    private final SubjectThreadFallback threads = new SubjectThreadFallback(indexes);

    private static Email mail(String project, String id, String thread, String from, List<String> to, String subject,
                              Instant at, Map<String, String> headers) {
        return new Email(id, new Scope(project, thread), from, to, subject, "Text of " + id, at, headers);
    }

    private static Email fresh(String id, String from, List<String> to, String subject, Instant at) {
        return mail("uni", id, id, from, to, subject, at, Map.of());
    }

    private void store(Email email) {
        recorder.onReceived(email, new Analysis(List.of(), List.of(), Map.of()));
    }

    private String threadOf(Email email) {
        return threads.resolve(email).scope().threadId();
    }

    /** The conversation of en-01 / en-02: a request, then the owner's reply with reply headers. */
    private void storeConversation() {
        store(fresh("<a1@x>", STUDENT, List.of(OWNER), "Extension for CS204 project", T0));
        store(mail("uni", "<a2@x>", "<a1@x>", OWNER, List.of(STUDENT), "Re: Extension for CS204 project",
                T0.plus(Duration.ofHours(5)), Map.of("In-Reply-To", "<a1@x>")));
    }

    @Test
    void aReplyWithoutHeadersBetweenTheSamePeopleJoinsTheThread() {
        storeConversation();
        Email reply = fresh("<a3@x>", STUDENT, List.of(OWNER), "RE : extension for  CS204 project", T0.plus(Duration.ofDays(1)));
        assertEquals("<a1@x>", threadOf(reply));
    }

    @Test
    void aNewcomerNeverJoins() {
        storeConversation();
        Email withCc = fresh("<a3@x>", STUDENT, List.of(OWNER, "friend@example.org"), "Re: Extension for CS204 project",
                T0.plus(Duration.ofDays(1)));
        assertEquals("<a3@x>", threadOf(withCc));
        Email stranger = fresh("<a4@x>", "other@students.example.edu", List.of(OWNER), "Re: Extension for CS204 project",
                T0.plus(Duration.ofDays(1)));
        assertEquals("<a4@x>", threadOf(stranger));
    }

    @Test
    void forwardsAndPlainSubjectsDoNotJoin() {
        storeConversation();
        assertEquals("<a3@x>", threadOf(fresh("<a3@x>", STUDENT, List.of(OWNER), "Fwd: Extension for CS204 project",
                T0.plus(Duration.ofDays(1)))));
        assertEquals("<a4@x>", threadOf(fresh("<a4@x>", STUDENT, List.of(OWNER), "Extension for CS204 project",
                T0.plus(Duration.ofDays(1)))));
    }

    @Test
    void onlyWithinTheWindowAndNeverFromTheFuture() {
        storeConversation();
        assertEquals("<late@x>", threadOf(fresh("<late@x>", STUDENT, List.of(OWNER), "Re: Extension for CS204 project",
                T0.plus(Duration.ofDays(31)))));
        assertEquals("<early@x>", threadOf(fresh("<early@x>", STUDENT, List.of(OWNER), "Re: Extension for CS204 project",
                T0.minus(Duration.ofHours(1)))));
    }

    @Test
    void twoCandidateThreadsMeanNoJoin() {
        storeConversation();
        store(fresh("<b1@x>", STUDENT, List.of(OWNER), "Re: Extension for CS204 project", T0.plus(Duration.ofHours(8))));
        // <b1@x> was stored as its own thread (stored directly, not resolved), so two threads now match.
        assertEquals("<a5@x>", threadOf(fresh("<a5@x>", STUDENT, List.of(OWNER), "Re: Extension for CS204 project",
                T0.plus(Duration.ofDays(1)))));
    }

    @Test
    void emailsWithReplyHeadersOrAnotherProjectAreLeftAlone() {
        storeConversation();
        Email referenced = mail("uni", "<a6@x>", "<elsewhere@x>", STUDENT, List.of(OWNER), "Re: Extension for CS204 project",
                T0.plus(Duration.ofDays(1)), Map.of("References", "<elsewhere@x>"));
        assertEquals("<elsewhere@x>", threadOf(referenced));
        Email otherProject = mail("other", "<a7@x>", "<a7@x>", STUDENT, List.of(OWNER), "Re: Extension for CS204 project",
                T0.plus(Duration.ofDays(1)), Map.of());
        assertEquals("<a7@x>", threadOf(otherProject));
    }
}
