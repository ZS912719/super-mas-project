package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;

class RuleMissingInformationCheckTest {
    private final RuleMissingInformationCheck check = new RuleMissingInformationCheck();

    static Email email(String subject) {
        return new Email("id", TopicTestSupport.HERE, "a@b.c", List.of(), subject, "body", TopicTestSupport.AT, Map.of());
    }

    static Analysis analysis(String text, String acts, String entities) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(MetadataAgent.KEY_CURRENT_TEXT, text);
        if (acts != null) {
            metadata.put(MetadataAgent.KEY_SPEECH_ACTS, acts);
        }
        if (entities != null) {
            metadata.put(MetadataAgent.KEY_ENTITIES, entities);
        }
        return new Analysis(List.of(), List.of(), metadata);
    }

    private static String acts(String act) {
        return "[{\"act\":\"" + act + "\",\"confidence\":1,\"source\":\"rules\"}]";
    }

    private static String entity(String type) {
        return "[{\"type\":\"" + type + "\",\"text\":\"x\",\"confidence\":1,\"source\":\"rules\"}]";
    }

    private Set<InformationField> missing(String subject, String text, String act, String entities) {
        return check.check(email(subject), analysis(text, acts(act), entities)).fields();
    }

    @Test
    void aMeetingRequestWithoutADateNeedsOne() {
        assertEquals(Set.of(InformationField.DATE), missing("Hello", "Could we meet?", "request", "[]"));
    }

    @Test
    void aMeetingRequestWithADateNeedsNothing() {
        assertEquals(Set.of(), missing("Hello", "Could we meet?", "request", entity("DATE")));
    }

    @Test
    void aDateOnlyInTheSubjectCounts() {
        assertEquals(Set.of(), missing("Meeting on January 12, 2002", "Could we meet?", "request", "[]"));
    }

    @Test
    void aProposalIsTreatedLikeARequestForMeetings() {
        assertEquals(Set.of(InformationField.DATE), missing("Hello", "Let us schedule it", "propose", "[]"));
    }

    @Test
    void aCommitmentNeedsATimeWhateverItSays() {
        assertEquals(Set.of(InformationField.DEADLINE), missing("Hello", "I will send it", "commit", "[]"));
    }

    @Test
    void courseAndReferenceRowsApply() {
        assertEquals(Set.of(InformationField.COURSE), missing("Hello", "Please check my grade", "request", "[]"));
        assertEquals(Set.of(InformationField.ID), missing("Hello", "Please check the invoice", "request", "[]"));
        assertEquals(Set.of(), missing("Hello", "Please check the invoice", "request", entity("ID")));
    }

    @Test
    void aMeetingWordWithoutARequestNeedsNothing() {
        assertEquals(Set.of(), missing("Hello", "The meeting went well", "deliver", "[]"));
    }

    @Test
    void aRequestWithoutATriggerWordNeedsNothing() {
        assertEquals(Set.of(), missing("Hello", "Please advise", "request", "[]"));
    }

    @Test
    void wordsMatchWhole() {
        assertEquals(Set.of(), missing("Hello", "Could you recall the meetings", "request", "[]"));
    }

    @Test
    void anUnreadableAnalysisReportsNothingInsteadOfFailing() {
        assertEquals(Set.of(), check.check(email("s"), analysis("Could we meet?", "not json", "not json")).fields());
        assertEquals(Set.of(), check.check(email(null), analysis("Could we meet?", null, null)).fields());
    }

    @Test
    void itReadsTheRealMetadataAgentOutput() {
        Email email = new Email("id", TopicTestSupport.HERE, "a@b.c", List.of(), "Hello",
                "Could you meet to discuss this?", TopicTestSupport.AT, Map.of());
        assertEquals(Set.of(InformationField.DATE), check.check(email, MetadataAgent.rulesOnly().analyze(email)).fields());
    }
}
