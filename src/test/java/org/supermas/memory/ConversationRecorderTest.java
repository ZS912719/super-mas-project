package org.supermas.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.context.ProjectIndexes;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Approval;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.ConversationMessage;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.DraftOrigin;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.model.Domain.Scope;
import org.supermas.memory.ConversationRecorder.SendConfirmation;

class ConversationRecorderTest {
    private static final Instant AT = Instant.parse("2026-11-03T10:00:00Z");
    private static final Scope SCOPE = new Scope("p1", "thread");
    private static final BoardId BOARD = new BoardId(SCOPE, "task-1");

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();
    private final IndexConversationRepository repository = new IndexConversationRepository(indexes,
            project -> Optional.of(new ContextSource("mail", project, URI.create("file:///unused"), Set.of("reader"))));
    private final ConversationRecorder recorder = new ConversationRecorder(repository, MessageTagger.none());

    @AfterEach
    void close() throws Exception {
        indexes.close();
    }

    private static Draft draft(int version, DraftOrigin origin, ReviewStatus review) {
        return new Draft(version, "Re: Question", "Answer " + version, "agent", "why", origin, review,
                AT.plusSeconds(version));
    }

    private List<ConversationMessage> history() {
        return repository.find(SCOPE, new HistoryQuery(Set.of(MessageKind.values()), 20));
    }

    @Test
    void aReceivedEmailIsStoredWithItsNewTextOnly() {
        Email email = new Email("<m1@example.org>", SCOPE, "a@example.org", List.of("me@example.org"), "Question",
                "New words\n> old quoted words", AT, Map.of());
        Analysis analysis = new Analysis(List.of(), List.of(), Map.of(MetadataAgent.KEY_CURRENT_TEXT, "New words"));

        assertTrue(recorder.onReceived(email, analysis));
        assertFalse(recorder.onReceived(email, analysis));

        assertEquals("New words", history().get(0).body());
        assertEquals("<m1@example.org>", history().get(0).id());
    }

    @Test
    void anEmailCannotTakeAnInternalIdentifier() {
        Email email = new Email(ConversationRecorder.INTERNAL_PREFIX + "x", SCOPE, "a@example.org", List.of(), "s",
                "b", AT, Map.of());
        assertThrows(SecurityException.class,
                () -> recorder.onReceived(email, new Analysis(List.of(), List.of(), Map.of())));
    }

    @Test
    void draftsKeepTheirKindByOrigin() {
        recorder.onDraft(BOARD, draft(1, DraftOrigin.MODEL_GENERATED, ReviewStatus.PROPOSED));
        recorder.onDraft(BOARD, draft(2, DraftOrigin.USER_EDITED, ReviewStatus.PROPOSED));

        assertEquals(List.of(MessageKind.GENERATED_DRAFT, MessageKind.USER_EDITED_DRAFT),
                history().stream().map(ConversationMessage::kind).toList());
    }

    @Test
    void onlyTheExactApprovedAndConfirmedVersionIsRecordedAsSent() {
        Approval approval = new Approval(BOARD, 2, AT);
        SendConfirmation confirmed = new SendConfirmation("smtp-42", AT.plusSeconds(5));

        assertThrows(SecurityException.class, () -> recorder.onSentConfirmed(BOARD,
                draft(1, DraftOrigin.MODEL_GENERATED, ReviewStatus.APPROVED), approval, confirmed), "stale version");
        assertThrows(SecurityException.class, () -> recorder.onSentConfirmed(BOARD,
                draft(2, DraftOrigin.USER_EDITED, ReviewStatus.PROPOSED), approval, confirmed), "not approved");
        assertThrows(SecurityException.class, () -> recorder.onSentConfirmed(BOARD,
                draft(2, DraftOrigin.USER_EDITED, ReviewStatus.APPROVED), approval, null), "unconfirmed");
        assertThrows(SecurityException.class, () -> recorder.onSentConfirmed(BOARD,
                draft(2, DraftOrigin.USER_EDITED, ReviewStatus.APPROVED), approval,
                new SendConfirmation("smtp-42", AT.minusSeconds(1))), "confirmed before approval");
        assertThrows(SecurityException.class, () -> recorder.onSentConfirmed(new BoardId(SCOPE, "other"),
                draft(2, DraftOrigin.USER_EDITED, ReviewStatus.APPROVED), approval, confirmed), "other board");
        assertTrue(history().isEmpty());

        assertTrue(recorder.onSentConfirmed(BOARD, draft(2, DraftOrigin.USER_EDITED, ReviewStatus.APPROVED),
                approval, confirmed));
        assertEquals(MessageKind.SENT, history().get(0).kind());
        // The index keeps the source, not the transport identifier: see the step 8.2 report.
        assertEquals("mail", history().get(0).provenance().sourceId());
        assertEquals(AT.plusSeconds(5), history().get(0).recordedAt());
    }

    @Test
    void aFailingListenerDoesNotLoseTheRecordAndOthersStillHear() {
        List<String> heard = new ArrayList<>();
        recorder.addListener(message -> { throw new IllegalStateException("broken"); });
        recorder.addListener(message -> heard.add(message.id()));

        recorder.onDraft(BOARD, draft(1, DraftOrigin.MODEL_GENERATED, ReviewStatus.PROPOSED));

        assertEquals(1, history().size());
        assertEquals(1, heard.size());
        assertEquals(1, recorder.listenerFailures());
    }
}
