package org.supermas.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.context.ProjectIndexes;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.ConversationMessage;
import org.supermas.model.Domain.HistoryQuery;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;

class IndexConversationRepositoryTest {
    private static final Instant AT = Instant.parse("2026-11-03T10:00:00Z");
    private static final Map<String, ContextSource> SOURCES = Map.of(
            "p1", new ContextSource("mail-p1", "p1", URI.create("file:///unused"), Set.of("reader")),
            "p2", new ContextSource("mail-p2", "p2", URI.create("file:///unused"), Set.of("reader")));

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();
    private final IndexConversationRepository repository =
            new IndexConversationRepository(indexes, project -> Optional.ofNullable(SOURCES.get(project)));

    @AfterEach
    void close() throws Exception {
        indexes.close();
    }

    private static ConversationMessage message(String id, Scope scope, MessageKind kind, String body, int minute) {
        Instant at = AT.plusSeconds(60L * minute);
        return new ConversationMessage(id, scope, kind, "Subject", body, new Provenance("test", "test", at), at);
    }

    @Test
    void equalThreadIdsInTwoProjectsStaySeparate() {
        repository.append(message("a", new Scope("p1", "t"), MessageKind.RECEIVED, "first project", 0));
        repository.append(message("b", new Scope("p2", "t"), MessageKind.RECEIVED, "second project", 0));

        List<ConversationMessage> found = repository.find(new Scope("p1", "t"),
                new HistoryQuery(Set.of(MessageKind.RECEIVED), 10));

        assertEquals(List.of("a"), found.stream().map(ConversationMessage::id).toList());
    }

    @Test
    void draftsAreNotReturnedWhenOnlyReceivedAndSentAreAsked() {
        Scope scope = new Scope("p1", "t");
        repository.append(message("in", scope, MessageKind.RECEIVED, "question", 0));
        repository.append(message("draft", scope, MessageKind.GENERATED_DRAFT, "unsent answer", 1));
        repository.append(message("edit", scope, MessageKind.USER_EDITED_DRAFT, "edited answer", 2));
        repository.append(message("out", scope, MessageKind.SENT, "sent answer", 3));

        List<ConversationMessage> found = repository.find(scope,
                new HistoryQuery(Set.of(MessageKind.RECEIVED, MessageKind.SENT), 10));

        assertEquals(List.of("in", "out"), found.stream().map(ConversationMessage::id).toList());
        assertEquals(MessageKind.SENT, found.get(1).kind());
    }

    @Test
    void domainSearchNeverReturnsAnUnsentDraft() {
        Scope scope = new Scope("p1", "t");
        repository.append(message("in", scope, MessageKind.RECEIVED, "budget question", 0));
        repository.append(message("draft", scope, MessageKind.GENERATED_DRAFT, "budget draft answer", 1));
        repository.append(message("edit", scope, MessageKind.USER_EDITED_DRAFT, "budget edited answer", 2));
        repository.append(message("out", scope, MessageKind.SENT, "budget sent answer", 3));

        List<String> found = indexes.forProject("p1").search("budget answer", Set.of("mail-p1"), "reader", Set.of(), 10)
                .stream().map(hit -> hit.document().id()).sorted().toList();

        assertEquals(List.of("in", "out"), found);
    }

    @Test
    void anIdenticalRepeatIsIgnoredAndAConflictingOneIsRefused() {
        Scope scope = new Scope("p1", "t");
        assertTrue(repository.appendIfAbsent(message("m", scope, MessageKind.RECEIVED, "text", 0), Set.of()));
        assertFalse(repository.appendIfAbsent(message("m", scope, MessageKind.RECEIVED, "text", 0), Set.of()));

        assertThrows(IllegalStateException.class,
                () -> repository.append(message("m", scope, MessageKind.SENT, "text", 0)));
        assertEquals(1, repository.find(scope, new HistoryQuery(Set.of(MessageKind.values()), 10)).size());
    }

    @Test
    void aProjectWithoutAConversationSourceCannotStoreHistory() {
        assertThrows(IllegalStateException.class,
                () -> repository.append(message("m", new Scope("unknown", "t"), MessageKind.RECEIVED, "x", 0)));
    }

    @Test
    void newestMessagesAreKeptWhenHistoryIsLimited() {
        Scope scope = new Scope("p1", "t");
        for (int i = 0; i < 5; i++) {
            repository.append(message("m" + i, scope, MessageKind.RECEIVED, "text " + i, i));
        }

        List<ConversationMessage> found = repository.find(scope, new HistoryQuery(Set.of(MessageKind.RECEIVED), 2));

        assertEquals(List.of("m3", "m4"), found.stream().map(ConversationMessage::id).toList());
    }
}
