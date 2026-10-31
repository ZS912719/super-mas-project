package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ContextQuery;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.RetrievalResult;
import org.supermas.model.Domain.Scope;

class DomainRetrievalTest {
    private static final Scope HERE = new Scope("project-a", "thread-1");
    private static final BoardId BOARD = new BoardId(HERE, "task");
    private static final URI LOCATION = URI.create("file:///unused");
    private static final Map<String, ContextSource> SOURCES = Map.of(
            "shared", new ContextSource("shared", "project-a", LOCATION, Set.of("specialist", "broad")),
            "restricted", new ContextSource("restricted", "project-a", LOCATION, Set.of("broad")),
            "foreign", new ContextSource("foreign", "project-b", LOCATION, Set.of("specialist", "broad")));
    private static final Map<String, AgentConfig> AGENTS = Map.of(
            "specialist", config("specialist", Set.of("shared", "foreign")),
            "broad", config("broad", Set.of("shared", "restricted", "foreign")));

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();
    private final LuceneContextRetriever retriever =
            new LuceneContextRetriever(indexes, SOURCES, id -> Optional.ofNullable(AGENTS.get(id)));

    private static AgentConfig config(String id, Set<String> sources) {
        return new AgentConfig(id, "role", "prompt", Set.of(), List.of(), sources, false);
    }

    private void document(String id, Scope scope, String source, String text, Set<String> permitted) {
        indexes.forProject(scope.projectId()).add(new IndexedDocument(id, scope, source, Optional.empty(), "en", "", text,
                permitted, Set.of(), Instant.parse("2026-11-01T10:00:00Z")));
    }

    private RetrievalResult search(String agent, Set<String> sources, int budget, boolean supplemental) {
        return retriever.retrieveDomain(new ContextQuery(BOARD, agent, sources, List.of("budget"), supplemental, budget));
    }

    private static List<String> ids(RetrievalResult result) {
        return result.evidence().stream().map(Evidence::id).toList();
    }

    @AfterEach
    void close() throws Exception {
        indexes.close();
    }

    @Test
    void aBroadAgentFetchingForASpecialistGetsOnlyTheSpecialistsRights() {
        document("open", HERE, "shared", "budget", Set.of("specialist", "broad"));
        document("closed", HERE, "restricted", "budget", Set.of("broad"));
        RetrievalResult forSpecialist = search("specialist", Set.of("shared", "restricted"), 1000, false);
        assertEquals(List.of("open"), ids(forSpecialist));
        assertTrue(forSpecialist.explanation().contains("restricted"));
        assertEquals(2, search("broad", Set.of("shared", "restricted"), 1000, false).evidence().size());
    }

    @Test
    void aSourceFromAnotherProjectIsDeniedBeforeSearching() {
        document("mine", HERE, "shared", "budget", Set.of("specialist"));
        indexes.forProject("project-a").add(new IndexedDocument("planted", HERE, "foreign", Optional.empty(), "en", "",
                "budget", Set.of("specialist"), Set.of(), Instant.parse("2026-11-01T10:00:00Z")));
        RetrievalResult result = search("specialist", Set.of("shared", "foreign"), 1000, false);
        assertEquals(List.of("mine"), ids(result));
        assertTrue(result.explanation().contains("foreign"));
    }

    @Test
    void equalSourcesAndThreadsInAnotherProjectAreNotSearched() {
        document("other", new Scope("project-b", "thread-1"), "shared", "budget", Set.of("specialist"));
        assertTrue(search("specialist", Set.of("shared"), 1000, false).evidence().isEmpty());
    }

    @Test
    void anUnknownAgentOrSourceGetsNothing() {
        document("open", HERE, "shared", "budget", Set.of("specialist"));
        assertTrue(search("nobody", Set.of("shared"), 1000, false).evidence().isEmpty());
        assertTrue(search("specialist", Set.of("invented"), 1000, false).evidence().isEmpty());
    }

    @Test
    void aSourcePermissionIsNotEnoughWithoutTheAgentsOwnConfiguration() {
        Map<String, AgentConfig> narrowed = Map.of("specialist", config("specialist", Set.of()));
        document("open", HERE, "shared", "budget", Set.of("specialist"));
        LuceneContextRetriever strict =
                new LuceneContextRetriever(indexes, SOURCES, id -> Optional.ofNullable(narrowed.get(id)));
        assertTrue(strict.retrieveDomain(new ContextQuery(BOARD, "specialist", Set.of("shared"), List.of("budget"),
                false, 1000)).evidence().isEmpty());
    }

    @Test
    void aDocumentLevelRestrictionAppliesInsideAnAllowedSource() {
        document("mine", HERE, "shared", "budget", Set.of("specialist"));
        document("not-mine", HERE, "shared", "budget", Set.of("broad"));
        assertEquals(List.of("mine"), ids(search("specialist", Set.of("shared"), 1000, false)));
    }

    @Test
    void evidenceRightsAreTheIntersectionAndCannotExceedTheSource() {
        document("wide", HERE, "shared", "budget", Set.of("specialist", "broad", "outsider"));
        Evidence evidence = search("specialist", Set.of("shared"), 1000, false).evidence().get(0);
        assertEquals(Set.of("specialist", "broad"), evidence.permittedAgentIds());
        assertEquals(EvidenceKind.RETRIEVED_EVIDENCE, evidence.kind());
    }

    @Test
    void evidenceIsScopedToTheBoardAndNamesItsOriginalThread() {
        document("elsewhere", new Scope("project-a", "thread-9"), "shared", "budget", Set.of("specialist"));
        Evidence evidence = search("specialist", Set.of("shared"), 1000, false).evidence().get(0);
        assertEquals(HERE, evidence.scope());
        assertTrue(evidence.provenance().description().contains("thread-9"));
    }

    @Test
    void resultsOverBudgetAreLeftOutWholeNeverCut() {
        document("small", HERE, "shared", "budget", Set.of("specialist"));
        document("large", HERE, "shared", "budget " + "x ".repeat(100), Set.of("specialist"));
        RetrievalResult result = search("specialist", Set.of("shared"), 10, false);
        assertEquals(List.of("small"), ids(result));
        assertTrue(result.explanation().contains("1 over budget"));
        assertTrue(search("specialist", Set.of("shared"), 0, false).evidence().isEmpty());
    }

    @Test
    void aSupplementalQuerySearchesDeeper() {
        for (int i = 0; i < 15; i++) {
            document("d" + i, HERE, "shared", "budget", Set.of("specialist"));
        }
        assertEquals(10, search("specialist", Set.of("shared"), 1000, false).evidence().size());
        assertEquals(15, search("specialist", Set.of("shared"), 1000, true).evidence().size());
    }

    @Test
    void noMatchIsANormalResult() {
        RetrievalResult result = search("specialist", Set.of("shared"), 1000, false);
        assertFalse(result.sufficient());
        assertTrue(result.evidence().isEmpty());
    }
}
