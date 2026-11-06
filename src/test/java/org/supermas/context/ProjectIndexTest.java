package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;

class ProjectIndexTest {
    private static final Scope SCOPE = new Scope("project", "thread");

    static IndexedDocument document(String id, Scope scope, String language, String text) {
        return new IndexedDocument(id, scope, "wiki", Optional.empty(), language, "", text, Set.of("agent"),
                Set.of(), Instant.parse("2026-11-01T10:00:00Z"));
    }

    private static List<String> ids(List<ProjectIndex.Hit> hits) {
        return hits.stream().map(hit -> hit.document().id()).toList();
    }

    private static List<ProjectIndex.Hit> search(ProjectIndex index, String text) {
        return index.search(text, Set.of("wiki"), "agent", Set.of(), 10);
    }

    @Test
    void englishAndFrenchWordFormsMatchThroughStemming() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            ProjectIndex index = indexes.forProject("project");
            index.add(document("en", SCOPE, "en", "The students submitted their reports"));
            index.add(document("fr", SCOPE, "fr", "Les rapports des étudiantes"));
            assertEquals(List.of("en"), ids(search(index, "student")));
            assertEquals(List.of("fr"), ids(search(index, "étudiante")));
        }
    }

    @Test
    void querySyntaxInEmailTextIsOnlyText() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            ProjectIndex index = indexes.forProject("project");
            index.add(document("secret", SCOPE, "en", "confidential salary"));
            assertTrue(search(index, "project:* OR *:*").isEmpty());
            assertTrue(search(index, "\"unbalanced ( [ {").isEmpty());
        }
    }

    @Test
    void textWithNoSearchableTermFindsNothingAndDoesNotFail() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            ProjectIndex index = indexes.forProject("project");
            index.add(document("a", SCOPE, "en", "budget"));
            assertTrue(search(index, "   ").isEmpty());
            assertTrue(search(index, null).isEmpty());
        }
    }

    @Test
    void aHugeQueryIsCappedInsteadOfFailing() throws Exception {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            huge.append("word").append(i).append(' ');
        }
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            ProjectIndex index = indexes.forProject("project");
            index.add(document("a", SCOPE, "en", "word1 budget"));
            assertEquals(List.of("a"), ids(search(index, huge.toString())));
        }
    }

    @Test
    void addingTheSameIdentifierReplacesTheDocument() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            ProjectIndex index = indexes.forProject("project");
            index.add(document("a", SCOPE, "en", "budget"));
            index.add(document("a", SCOPE, "en", "holiday"));
            assertEquals(1, index.size());
            assertTrue(search(index, "budget").isEmpty());
            assertEquals(List.of("a"), ids(search(index, "holiday")));
        }
    }

    @Test
    void aDocumentFromAnotherProjectIsRefused() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            ProjectIndex index = indexes.forProject("project");
            IndexedDocument foreign = document("a", new Scope("other", "thread"), "en", "budget");
            assertThrows(SecurityException.class, () -> index.add(foreign));
            assertThrows(SecurityException.class, () -> index.addAll(List.of(foreign)));
            assertEquals(0, index.size());
        }
    }

    @Test
    void equalIdentifiersInDifferentProjectsStaySeparate() throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            indexes.forProject("a").add(document("same", new Scope("a", "thread"), "en", "budget"));
            indexes.forProject("b").add(document("same", new Scope("b", "thread"), "en", "holiday"));
            assertEquals(1, indexes.forProject("a").size());
            assertTrue(search(indexes.forProject("b"), "budget").isEmpty());
        }
    }

    @Test
    void storedFieldsSurviveAnIndexRoundTrip(@TempDir Path folder) throws Exception {
        IndexedDocument original = new IndexedDocument("m", SCOPE, "mail", Optional.of(MessageKind.SENT), "fr", "Sujet",
                "Bonjour budget", Set.of("x", "y"), Set.of("topic"), Instant.parse("2026-11-01T10:00:00.123Z"));
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(folder)) {
            indexes.forProject("project").add(original);
            indexes.forProject("project").commit();
        }
        try (ProjectIndexes reopened = ProjectIndexes.onDisk(folder)) {
            assertEquals(original, reopened.forProject("project")
                    .search("budget", Set.of("mail"), "x", Set.of(), 1).get(0).document());
        }
    }

    @Test
    void aProjectIdentifierCannotEscapeTheRootFolder(@TempDir Path folder) throws Exception {
        try (ProjectIndexes indexes = ProjectIndexes.onDisk(folder.resolve("root"))) {
            indexes.forProject("../../escape").add(document("a", new Scope("../../escape", "t"), "en", "budget"));
            indexes.forProject("../../escape").commit();
        }
        assertFalse(Files.exists(folder.resolve("escape")));
        try (var listing = Files.list(folder)) {
            assertEquals(List.of(folder.resolve("root")), listing.toList());
        }
    }
}
