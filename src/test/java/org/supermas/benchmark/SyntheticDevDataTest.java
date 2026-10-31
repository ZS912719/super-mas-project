package org.supermas.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SyntheticDevDataTest {
    private static final Set<String> ENTITY_TYPES = Set.of("PERSON", "COURSE", "DATE", "DEADLINE", "ID");
    private static final Set<String> SPEECH_ACTS = Set.of("request", "propose", "commit", "deliver");
    private static final Set<String> ROLES = Set.of("student", "teacher", "administration", "researcher");

    private final Dataset dataset = load();

    private static Dataset load() {
        try {
            return Dataset.load(Path.of("benchmark/data/synthetic-dev"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void containsTwelveEmailsPerLanguage() {
        assertEquals(12, dataset.cases().stream().filter(c -> "en".equals(c.gold().language())).count());
        assertEquals(12, dataset.cases().stream().filter(c -> "fr".equals(c.gold().language())).count());
    }

    @Test
    void labelsFollowTheSchema() {
        for (Dataset.Case c : dataset.cases()) {
            Extraction gold = c.gold();
            assertTrue(gold.roleHint() == null || ROLES.contains(gold.roleHint()), c.id());
            assertTrue(SPEECH_ACTS.containsAll(gold.speechActs()), c.id());
            for (Extraction.Entity entity : gold.entities()) {
                assertTrue(ENTITY_TYPES.contains(entity.type()), c.id());
                assertTrue(gold.currentText().contains(entity.text()), c.id() + ": " + entity.text());
            }
        }
    }
}
