package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.RetrievalResult;
import org.supermas.model.Domain.Scope;

class ThreadRetrievalTest {
    private static final Scope A = new Scope("project-a", "thread-1");
    private static final Scope B = new Scope("project-b", "thread-1");
    private static final Set<MessageKind> ALL = Set.of(MessageKind.values());

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();
    private final LuceneContextRetriever retriever =
            new LuceneContextRetriever(indexes, Map.of(), id -> Optional.empty());

    private void message(String id, Scope scope, MessageKind kind, int day) {
        indexes.forProject(scope.projectId()).add(new IndexedDocument(id, scope, "conversation", Optional.of(kind), "en",
                "Subject", "Body of " + id, Set.of("agent"), Set.of(),
                Instant.parse("2026-11-01T10:00:00Z").plusSeconds(day * 86400L)));
    }

    private List<String> ids(RetrievalResult result) {
        return result.evidence().stream().map(Evidence::id).toList();
    }

    private RetrievalResult thread(Scope scope, Set<MessageKind> kinds, int max) {
        return retriever.retrieveThread(new BoardId(scope, "task"), new HistoryQuery(kinds, max));
    }

    @AfterEach
    void close() throws Exception {
        indexes.close();
    }

    @Test
    void equalThreadIdentifiersInAnotherProjectAreNotReturned() {
        message("in-a", A, MessageKind.RECEIVED, 1);
        message("in-b", B, MessageKind.RECEIVED, 1);
        assertEquals(List.of("in-a"), ids(thread(A, ALL, 10)));
        assertEquals(List.of("in-b"), ids(thread(B, ALL, 10)));
    }

    @Test
    void anotherThreadInTheSameProjectIsNotReturnedAndMatchingIsExact() {
        message("mine", A, MessageKind.RECEIVED, 1);
        message("other", new Scope("project-a", "thread-2"), MessageKind.RECEIVED, 1);
        message("case", new Scope("project-a", "THREAD-1"), MessageKind.RECEIVED, 1);
        message("prefix", new Scope("project-a", "thread-1-extra"), MessageKind.RECEIVED, 1);
        assertEquals(List.of("mine"), ids(thread(A, ALL, 10)));
    }

    @Test
    void onlyRequestedKindsAreReturnedSoADraftIsNeverASentMessage() {
        message("received", A, MessageKind.RECEIVED, 1);
        message("draft", A, MessageKind.GENERATED_DRAFT, 2);
        message("edited", A, MessageKind.USER_EDITED_DRAFT, 3);
        message("sent", A, MessageKind.SENT, 4);
        assertEquals(List.of("received", "sent"), ids(thread(A, Set.of(MessageKind.RECEIVED, MessageKind.SENT), 10)));
        assertTrue(thread(A, Set.of(), 10).evidence().isEmpty());
        assertTrue(thread(A, ALL, 10).evidence().get(1).provenance().description().contains("GENERATED_DRAFT"));
    }

    @Test
    void theLimitKeepsTheNewestMessagesInTimeOrder() {
        message("day3", A, MessageKind.RECEIVED, 3);
        message("day1", A, MessageKind.RECEIVED, 1);
        message("day2", A, MessageKind.RECEIVED, 2);
        assertEquals(List.of("day2", "day3"), ids(thread(A, ALL, 2)));
        assertTrue(thread(A, ALL, 0).evidence().isEmpty());
    }

    @Test
    void domainDocumentsAreNeverThreadHistory() {
        indexes.forProject("project-a").add(ProjectIndexTest.document("wiki", A, "en", "text"));
        assertTrue(thread(A, ALL, 10).evidence().isEmpty());
    }

    @Test
    void aNewThreadIsANormalEmptyResult() {
        RetrievalResult result = thread(A, ALL, 10);
        assertTrue(result.evidence().isEmpty());
        assertFalse(result.sufficient());
    }

    @Test
    void resultsAreLabelledAsRetrievedEvidenceWithSourceAndPermissions() {
        message("m", A, MessageKind.RECEIVED, 1);
        Evidence evidence = thread(A, ALL, 10).evidence().get(0);
        assertEquals(EvidenceKind.RETRIEVED_EVIDENCE, evidence.kind());
        assertEquals("conversation", evidence.provenance().sourceId());
        assertEquals(A, evidence.scope());
        assertEquals(Set.of("agent"), evidence.permittedAgentIds());
    }
}
