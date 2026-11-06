package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEntityExtractorTest {
    private final RuleEntityExtractor rules = new RuleEntityExtractor();

    private List<String> found(String text) {
        return rules.extract(text).stream().map(m -> m.type() + ":" + m.text()).toList();
    }

    @Test
    void separatesDeadlinesFromDatesByTheWordsBefore() {
        assertEquals(List.of("DEADLINE:Friday 6 November", "DATE:Monday"),
                found("The deadline is Friday 6 November. We met on Monday."));
    }

    @Test
    void findsCourseCodesAndNumbersAfterACue() {
        assertTrue(found("About CS204, my student number is 20231187.")
                .containsAll(List.of("COURSE:CS204", "ID:20231187")));
        assertEquals(List.of("ID:41983"), found("Your order #41983 has shipped."));
    }

    @Test
    void takesPersonsOnlyFromGreetingsWithoutTitles() {
        assertEquals(List.of("PERSON:Martin"), found("Dear Prof. Martin,\nThe room is free."));
        assertEquals(List.of(), found("The Board of Directors met."));
    }

    @Test
    void doesNotTreatAPhoneNumberAsAnIdentifier() {
        assertEquals(List.of(), found("Please call 713-853-7151."));
    }
}
