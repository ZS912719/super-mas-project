package org.supermas.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.supermas.benchmark.Extraction.Entity;

class ScorerTest {
    private static final Extraction GOLD = new Extraction("a@example.edu", List.of("b@example.edu"),
            "2026-11-03T08:12:00Z", "<1@example.edu>", "Hello there", List.of(new Entity("PERSON", "Élise Martin")),
            List.of("request"), null, "fr");

    private static Map<String, Scorer.FieldScore> score(Extraction predicted) {
        return Scorer.score(List.of(new Scorer.Pair(GOLD, predicted)));
    }

    @Test
    void perfectPredictionScoresOne() {
        Map<String, Scorer.FieldScore> scores = score(GOLD);
        assertEquals(1.0, scores.get("sender").f1());
        assertEquals(1.0, scores.get("entity:PERSON").f1());
        assertEquals(1.0, Scorer.macroF1(scores));
    }

    @Test
    void emptyPredictionHasZeroRecallAndUndefinedPrecision() {
        Scorer.FieldScore sender = score(Extraction.empty()).get("sender");
        assertEquals(0.0, sender.recall());
        assertEquals(0.0, sender.f1());
        assertNull(sender.precision());
        assertEquals(0.0, Scorer.macroF1(score(Extraction.empty())));
    }

    @Test
    void fieldsWithoutGoldValuesAreLeftOutOfMacroF1() {
        Scorer.FieldScore course = score(GOLD).get("entity:COURSE");
        assertNull(course.f1());
        assertEquals(1.0, Scorer.macroF1(score(GOLD)));
    }

    @Test
    void nullAccuracyPenalizesInventedValues() {
        Extraction invented = new Extraction(null, List.of(), null, null, null, List.of(), List.of(), "student", null);
        Scorer.FieldScore role = score(invented).get("roleHint");
        assertEquals(0.0, role.nullAccuracy());
        assertEquals(1, role.falsePositives());
        assertEquals(1.0, score(GOLD).get("roleHint").nullAccuracy());
    }

    @Test
    void comparisonIgnoresCaseAndWhitespaceButNotAccents() {
        Extraction variant = new Extraction(null, List.of(), null, null, "  HELLO\n  there ",
                List.of(new Entity("PERSON", "Elise Martin")), List.of(), null, null);
        assertEquals(1.0, score(variant).get("currentText").f1());
        assertEquals(0.0, score(variant).get("entity:PERSON").f1());
    }

    @Test
    void entityMustMatchType() {
        Extraction wrongType = new Extraction(null, List.of(), null, null, null,
                List.of(new Entity("COURSE", "Élise Martin")), List.of(), null, null);
        assertEquals(0, score(wrongType).get("entity:PERSON").truePositives());
        assertEquals(1, score(wrongType).get("entity:COURSE").falsePositives());
    }

    @Test
    void duplicatePredictionCountsAsFalsePositive() {
        Extraction duplicated = new Extraction(null, List.of(), null, null, null, List.of(),
                List.of("request", "request"), null, null);
        Scorer.FieldScore acts = score(duplicated).get("speechActs");
        assertEquals(1, acts.truePositives());
        assertEquals(1, acts.falsePositives());
    }
}
