package org.supermas.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.context.LuceneContextRetriever;
import org.supermas.context.ProjectIndexes;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextQuery;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.Scope;

class KnowledgeLoaderTest {
    private static final BoardId BOARD = new BoardId(new Scope("uni", "thread"), "task");

    @TempDir
    Path folder;

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();

    @AfterEach
    void close() throws Exception {
        indexes.close();
    }

    private void write(String relative, String content) throws IOException {
        Path file = folder.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void twoSources() throws IOException {
        write("sources.json", """
                [{"id": "student-faq", "project": "uni", "readers": ["student"]},
                 {"id": "staff-only", "project": "uni", "readers": ["administration"]}]""");
        write("student-faq/extensions.md", "# Extensions\nAn extension request needs a medical certificate.");
        write("student-faq/notes.pdf", "not loaded");
        write("staff-only/salaries.md", "# Salaries\nExtension extension certificate salary scale.");
    }

    private List<String> search(KnowledgeLoader.Report report, String agent, String words) {
        AgentConfig config = new AgentConfig(agent, "r", "", Set.of(), List.of(), Set.of("student-faq", "staff-only"),
                false);
        LuceneContextRetriever retriever = new LuceneContextRetriever(indexes, report.sources(),
                id -> id.equals(agent) ? Optional.of(config) : Optional.empty());
        return retriever.retrieveDomain(new ContextQuery(BOARD, agent, Set.of("student-faq", "staff-only"),
                List.of(words), false, 10_000)).evidence().stream().map(Evidence::id).toList();
    }

    @Test
    void eachAgentFindsOnlyTheSourcesItMayRead() throws IOException {
        twoSources();
        KnowledgeLoader.Report report = new KnowledgeLoader(indexes).load(folder);

        assertEquals(2, report.documents());
        assertEquals(List.of("kb:student-faq:extensions.md"), search(report, "student", "extension certificate"));
        assertEquals(List.of("kb:staff-only:salaries.md"), search(report, "administration", "extension certificate"));
        assertEquals(List.of(), search(report, "stranger", "extension certificate"));
    }

    @Test
    void reloadingASourceDropsDeletedFiles() throws IOException {
        twoSources();
        KnowledgeLoader loader = new KnowledgeLoader(indexes);
        loader.load(folder);
        Files.delete(folder.resolve("student-faq/extensions.md"));
        write("student-faq/exams.md", "# Exams\nExam rules: an extension is not possible for exams.");

        KnowledgeLoader.Report report = loader.load(folder);

        assertEquals(List.of("kb:student-faq:exams.md"), search(report, "student", "extension"));
    }

    @Test
    void aLinkLeavingTheFolderIsNotFollowed(@TempDir Path outside) throws IOException {
        twoSources();
        Files.writeString(outside.resolve("secret.md"), "extension secret");
        Files.createSymbolicLink(folder.resolve("student-faq/link.md"), outside.resolve("secret.md"));

        KnowledgeLoader.Report report = new KnowledgeLoader(indexes).load(folder);

        assertEquals(2, report.documents());
        assertTrue(search(report, "student", "secret").isEmpty());
    }

    @Test
    void aSourceIdentifierCannotEscapeTheFolder() throws IOException {
        write("sources.json", "[{\"id\": \"../etc\", \"project\": \"uni\", \"readers\": [\"student\"]}]");
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeLoader(indexes).load(folder));
    }

    @Test
    void theSourceListCarriesItsReadersAndProject() throws IOException {
        twoSources();
        Map<String, org.supermas.model.Domain.ContextSource> sources = new KnowledgeLoader(indexes).load(folder).sources();
        assertEquals(Set.of("student"), sources.get("student-faq").permittedAgentIds());
        assertEquals("uni", sources.get("staff-only").projectId());
    }
}
