package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleRoleHintClassifierTest {
    private final RuleRoleHintClassifier classifier = new RuleRoleHintClassifier();

    @Test
    void usesWhatTheSenderSaysAboutThemselves() {
        assertEquals(Optional.of("student"), classifier.classify("I asked my thesis supervisor."));
        assertEquals(Optional.of("teacher"), classifier.classify("I teach this course."));
        assertEquals(Optional.of("researcher"), classifier.classify("Our paper was accepted."));
    }

    @Test
    void staysEmptyWithoutClearEvidence() {
        assertEquals(Optional.empty(), classifier.classify("The students left early."));
        assertEquals(Optional.empty(), classifier.classify(null));
    }
}
