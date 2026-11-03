package org.supermas.agent.role;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.supermas.context.ContextSelector;
import org.supermas.context.LuceneContextRetriever;
import org.supermas.context.SenderVerifier;
import org.supermas.context.TopicContextSelector;
import org.supermas.llm.GenerationService;
import org.supermas.model.Domain.ModelConfig;

/**
 * What a role agent works with, injected by whoever builds the agents. The generation service and the model
 * configuration belong to the team's LLM integration; without one, an agent writes its plan and no draft.
 *
 * @param retriever the agent's own search of its sources, or empty to rely only on the board
 * @param selector chooses the agent's context from the board within its budget, with its rights
 * @param generation turns a model request into a draft, or empty for plans only
 * @param model passed to the generation service unchanged
 * @param requests finds what the sender asks for
 * @param senderVerifier decides whether a sender is verified, for roles that require it
 */
public record RoleServices(Optional<LuceneContextRetriever> retriever, ContextSelector selector,
                           Optional<GenerationService> generation, ModelConfig model, RequestExtractor requests,
                           SenderVerifier senderVerifier) {
    /** A local placeholder endpoint: the real adapter must enforce local-only inference. */
    public static final ModelConfig UNCONFIGURED_MODEL = new ModelConfig("unconfigured",
            URI.create("http://127.0.0.1:0/"), Duration.ofSeconds(30), 4000);

    /** Plans only: no search of its own, no generation, rule extraction, nobody verified. */
    public static RoleServices planOnly() {
        return new RoleServices(Optional.empty(), new TopicContextSelector(), Optional.empty(), UNCONFIGURED_MODEL,
                new RuleRequestExtractor(), SenderVerifier.none());
    }

    public RoleServices withRetriever(LuceneContextRetriever value) {
        return new RoleServices(Optional.of(value), selector, generation, model, requests, senderVerifier);
    }

    public RoleServices withGeneration(GenerationService value, ModelConfig config) {
        return new RoleServices(retriever, selector, Optional.of(value), config, requests, senderVerifier);
    }

    public RoleServices withRequests(RequestExtractor value) {
        return new RoleServices(retriever, selector, generation, model, value, senderVerifier);
    }

    public RoleServices withSenderVerifier(SenderVerifier value) {
        return new RoleServices(retriever, selector, generation, model, requests, value);
    }
}
