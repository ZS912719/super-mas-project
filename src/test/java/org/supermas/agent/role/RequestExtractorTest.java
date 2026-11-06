package org.supermas.agent.role;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.ModelResponse;

class RequestExtractorTest {
    private static final String EMAIL = "Hello,\nI missed the lab.\nCould I take it next week?\n> Can you reply? (quoted)\n"
            + "Please send me the slides. Thanks.";

    private static LlmRequestExtractor answering(ModelResponse response) {
        return new LlmRequestExtractor((request, config) -> response, RoleServices.UNCONFIGURED_MODEL);
    }

    @Test
    void rulesKeepQuestionsAndRequestsVerbatimAndSkipQuotedLines() {
        assertEquals(List.of("Could I take it next week?", "Please send me the slides."),
                new RuleRequestExtractor().extract(EMAIL, "en").requests());
        assertEquals(List.of("Pourriez-vous me confirmer la date ?"),
                new RuleRequestExtractor().extract("Bonjour. Pourriez-vous me confirmer la date ? Merci.", "fr").requests());
    }

    @Test
    void aValidQuotingAnswerIsUsed() {
        RequestExtractor.Requests found = answering(new ModelResponse(
                Optional.of("{\"requests\": [\"Could I take it next week?\"]}"), Optional.empty())).extract(EMAIL, "en");
        assertEquals(List.of("Could I take it next week?"), found.requests());
        assertEquals(LlmRequestExtractor.SOURCE, found.source());
    }

    @Test
    void anInventedOrRewrittenQuoteRejectsTheWholeAnswer() {
        RequestExtractor.Requests found = answering(new ModelResponse(Optional.of(
                "{\"requests\": [\"Could I take it next week?\", \"Please approve my extension.\"]}"), Optional.empty()))
                .extract(EMAIL, "en");
        assertEquals(LlmRequestExtractor.FALLBACK_SOURCE, found.source());
        assertEquals(new RuleRequestExtractor().extract(EMAIL, "en").requests(), found.requests());
    }

    @Test
    void failuresAndInvalidOutputFallBackToTheRules() {
        for (ModelResponse response : List.of(
                new ModelResponse(Optional.empty(), Optional.of(new Failure(FailureKind.CONNECTION, "down", true))),
                new ModelResponse(Optional.of("not json"), Optional.empty()),
                new ModelResponse(Optional.of("{\"answer\": []}"), Optional.empty()))) {
            assertEquals(LlmRequestExtractor.FALLBACK_SOURCE, answering(response).extract(EMAIL, "en").source());
        }
        LlmRequestExtractor throwing = new LlmRequestExtractor((request, config) -> {
            throw new IllegalStateException("timeout");
        }, RoleServices.UNCONFIGURED_MODEL);
        assertEquals(LlmRequestExtractor.FALLBACK_SOURCE, throwing.extract(EMAIL, "en").source());
    }

    @Test
    void theModelReceivesTheEmailOnlyAsUntrustedData() {
        String[] seen = new String[2];
        new LlmRequestExtractor((request, config) -> {
            seen[0] = request.systemPrompt();
            seen[1] = request.untrustedData();
            return new ModelResponse(Optional.of("{\"requests\": []}"), Optional.empty());
        }, RoleServices.UNCONFIGURED_MODEL).extract(EMAIL, "en");
        assertEquals(false, seen[0].contains("missed the lab"));
        assertEquals(EMAIL, seen[1]);
    }
}
