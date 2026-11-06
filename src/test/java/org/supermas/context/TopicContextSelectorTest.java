package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.ContextSelection;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.prototype.InMemoryBlackboard;

class TopicContextSelectorTest {
    private static final AgentConfig READER = new AgentConfig("reader", "r", "", Set.of(), List.of(),
            Set.of("wiki"), false);
    private static final int NO_LIMIT = 1_000_000;

    private final TopicContextSelector selector = new TopicContextSelector();
    private final InMemoryBlackboard board = TopicTestSupport.board(TopicTestSupport.HERE, "s", "t");
    private final List<Evidence> entries = new ArrayList<>();

    private void add(String document, String topic, String text) {
        add(document, topic, text, "wiki", Set.of("reader"), EvidenceKind.RETRIEVED_EVIDENCE);
    }

    private void add(String document, String topic, String text, String source, Set<String> permitted, EvidenceKind kind) {
        entries.add(new Evidence(TopicEvidence.id(document, topic), TopicTestSupport.HERE, kind,
                new Provenance(source, "test", TopicTestSupport.AT), text, permitted));
    }

    private ContextSelection select(int budget) {
        board.addEvidence(entries);
        entries.clear();
        return selector.select(board, READER, budget);
    }

    private static List<String> ids(ContextSelection selection) {
        return selection.evidence().stream().map(Evidence::id).toList();
    }

    @Test
    void aDocumentFoundByManyTopicsAppearsOnceAndNamesThemAll() {
        add("doc", "topic-b", "alpha");
        add("doc", "topic-a", "alpha");
        ContextSelection selection = select(NO_LIMIT);
        assertEquals(List.of("doc"), ids(selection));
        assertTrue(selection.evidence().get(0).provenance().description().endsWith("found by topics: topic-a, topic-b"));
    }

    @Test
    void agreementBetweenTopicsOutranksASingleHighRank() {
        add("only-a", "a", "one");
        add("both", "a", "two");
        add("both", "b", "two");
        assertEquals(List.of("both", "only-a"), ids(select(NO_LIMIT)));
    }

    @Test
    void fusionUsesRanksNotHowManyEntriesATopicHas() {
        add("first-of-a", "a", "aa");
        add("second-of-a", "a", "bb");
        add("first-of-b", "b", "cc");
        // Equal fused rank between the two firsts, broken by identifier for a stable order.
        assertEquals(List.of("first-of-a", "first-of-b", "second-of-a"), ids(select(NO_LIMIT)));
    }

    @Test
    void aNearCopyIsPushedBehindSomethingDifferent() {
        String text = "the quarterly budget report for the eastern region";
        add("original", "a", text);
        add("near-copy", "a", text + " again");
        add("different", "a", "travel arrangements for the visit next week");
        assertEquals(List.of("original", "different", "near-copy"), ids(select(NO_LIMIT)));
    }

    @Test
    void theBudgetOmitsWholeDocumentsAndNamesTheirSource() {
        add("long", "a", "x".repeat(50));
        add("short", "a", "y".repeat(10));
        ContextSelection selection = select(20);
        assertEquals(List.of("short"), ids(selection));
        assertEquals(10, selection.usedCharacters());
        assertEquals(List.of("wiki"), selection.omittedSourceIds());
    }

    @Test
    void theRequestersRightsDecideEvenIfTheViewShowsEverything() {
        add("not-for-reader", "a", "aa", "wiki", Set.of("broad"), EvidenceKind.RETRIEVED_EVIDENCE);
        add("source-not-allowed", "a", "bb", "hr-files", Set.of("reader"), EvidenceKind.RETRIEVED_EVIDENCE);
        add("fine", "a", "cc");
        assertEquals(List.of("fine"), ids(select(NO_LIMIT)));
    }

    @Test
    void onlyRetrievedEvidenceIsSelected() {
        add("inference", "reader", "guess", "wiki", Set.of("reader"), EvidenceKind.MODEL_INFERENCE);
        assertTrue(select(NO_LIMIT).evidence().isEmpty());
    }

    @Test
    void anEmptyBoardGivesAnEmptySelection() {
        ContextSelection selection = select(NO_LIMIT);
        assertTrue(selection.evidence().isEmpty());
        assertEquals(0, selection.usedCharacters());
    }

    @Test
    void theSameBoardAlwaysGivesTheSameOrder() {
        add("a", "x", "one two");
        add("b", "y", "one three");
        add("c", "x", "four five");
        assertEquals(ids(select(NO_LIMIT)), ids(select(NO_LIMIT)));
    }
}
