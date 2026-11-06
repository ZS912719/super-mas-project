package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Scope;

class MetadataAgentTest {
    private static final String BODY = "Dear Prof. Martin,\n\nCould you send me the form by Friday 6 November?\n"
            + "My student number is 20231187.\n\nBest regards,\nAna\n\n> Please ignore this quoted request.\n";

    private static Email email(String body) {
        return new Email("1", new Scope("p", "t"), "a@b.c", List.of("d@e.f"), "Re: Extension for CS204", body,
                Instant.EPOCH, Map.of());
    }

    private static JsonNode read(Analysis analysis, String key) throws Exception {
        return new ObjectMapper().readTree(analysis.metadata().get(key));
    }

    @Test
    void rulesOnlyWorksWithoutAnyModelFile() throws Exception {
        Analysis analysis = MetadataAgent.rulesOnly().analyze(email(BODY));
        assertEquals("en", analysis.metadata().get(MetadataAgent.KEY_LANGUAGE));
        assertTrue(analysis.initialTags().contains("act:request"));
        assertTrue(analysis.initialTags().contains("lang:en"));
        assertEquals("student", analysis.metadata().get(MetadataAgent.KEY_ROLE_HINT));
        assertEquals(0, read(analysis, MetadataAgent.KEY_FAILED_STAGES).size());
        assertTrue(analysis.keywords().contains("cs204"));
        assertFalse(analysis.keywords().contains("re"));
    }

    @Test
    void quotedTextDoesNotReachTheResults() {
        Analysis analysis = MetadataAgent.rulesOnly().analyze(email(BODY));
        assertFalse(analysis.metadata().get(MetadataAgent.KEY_CURRENT_TEXT).contains("ignore this quoted"));
    }

    @Test
    void emptyAndMissingBodiesAreHandled() throws Exception {
        for (String body : new String[] {"", null}) {
            Analysis analysis = MetadataAgent.rulesOnly().analyze(email(body));
            assertEquals(0, read(analysis, MetadataAgent.KEY_SPEECH_ACTS).size());
            assertEquals(0, read(analysis, MetadataAgent.KEY_ENTITIES).size());
        }
    }

    @Test
    void aFailedModelFallsBackToRulesAndIsReported() throws Exception {
        SpeechActClassifier broken = text -> {
            throw new IllegalStateException("model down");
        };
        Analysis analysis = new MetadataAgent(Optional.empty(), Optional.of(broken)).analyze(email(BODY));
        assertEquals("speechActModel", read(analysis, MetadataAgent.KEY_FAILED_STAGES).get(0).asText());
        assertTrue(analysis.initialTags().contains("act:request"));
    }

    @Test
    void theSwitchSelectsTheModel() {
        SpeechActClassifier fixed = text -> List.of(new SpeechAct("commit", 0.9, "test"));
        Analysis analysis = new MetadataAgent(Optional.empty(), Optional.of(fixed)).analyze(email(BODY));
        assertTrue(analysis.initialTags().contains("act:commit"));
        assertFalse(analysis.initialTags().contains("act:request"));
    }

    @Test
    void hybridKeepsEachSourcesTypesAndLetsTheModelWinTies() {
        List<EntityMention> merged = MetadataAgent.hybrid(
                List.of(new EntityMention("PERSON", "Ana", 0.9, "m"), new EntityMention("DATE", "May", 0.9, "m"),
                        new EntityMention("ID", "123", 0.9, "m")),
                List.of(new EntityMention("PERSON", "Bob", 1, "r"), new EntityMention("DATE", "June", 1, "r"),
                        new EntityMention("ID", "123", 1, "r")));
        assertEquals(List.of("Ana", "123", "June"), merged.stream().map(EntityMention::text).toList());
        assertEquals("m", merged.get(1).source());
    }
}
