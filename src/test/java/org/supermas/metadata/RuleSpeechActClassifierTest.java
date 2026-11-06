package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleSpeechActClassifierTest {
    private final RuleSpeechActClassifier classifier = new RuleSpeechActClassifier();

    private List<String> acts(String text) {
        return classifier.classify(text).stream().map(SpeechAct::act).toList();
    }

    @Test
    void recognizesTheSchemaExamples() {
        assertEquals(List.of("request"), acts("Could you send me the form?"));
        assertEquals(List.of("propose"), acts("Shall we meet on Tuesday?"));
        assertEquals(List.of("commit"), acts("I will review it tomorrow."));
        assertEquals(List.of("deliver"), acts("The grades are attached."));
    }

    @Test
    void pleaseFindAttachedDeliversWithoutRequesting() {
        assertEquals(List.of("deliver"), acts("Please find attached the report."));
    }

    @Test
    void oneEmailCanCarrySeveralActs() {
        assertEquals(List.of("request", "deliver"), acts("Here is the draft. Please review it."));
    }

    @Test
    void plainInformationAndEmptyTextHaveNoAct() {
        assertEquals(List.of(), acts("The office was quiet today."));
        assertEquals(List.of(), acts(""));
        assertEquals(List.of(), acts(null));
    }

    @Test
    void phrasesMustBeWholeWords() {
        assertEquals(List.of(), acts("The illustration pleased everyone."));
    }
}
