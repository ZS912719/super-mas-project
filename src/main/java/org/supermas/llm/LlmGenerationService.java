package org.supermas.llm;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.ContextSelection;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.GenerationResult;
import org.supermas.model.Domain.ModelConfig;
import org.supermas.model.Domain.ModelRequest;
import org.supermas.model.Domain.ModelResponse;

/**
 * Turns a local model's answer into a draft proposal. The model output is the reply body only; the subject is left
 * empty so the agent derives it from the email, and nothing internal (plans, evidence) is concatenated into the
 * reply. Output that still carries one of the prompt's block labels is refused, because it means the model echoed its
 * input instead of writing a reply. A bracketed placeholder at the very end ("[Your Name]") is removed and noted in
 * the rationale, because the person who sends the reply signs it.
 *
 * <p>The agents build their own requests (prompt management stays with the specialized agents and the composer),
 * so {@link #prepare} is not supported, as in the template stand-in.
 */
public final class LlmGenerationService implements GenerationService {
    /**
     * A block label of the prompt ({@code RolePromptBuilder}). Only these mean the model echoed its input: any
     * "<<" and ">>" also appear in French replies typed with ASCII quotation marks (step 13, run B).
     */
    static final Pattern PROMPT_BLOCK = Pattern.compile("<<\\s*(EMAIL (ADDRESSES|SUBJECT|TEXT)|REPLY PLAN|CONTEXT id=|FEEDBACK ON THE PREVIOUS DRAFT)");

    /**
     * A closing line followed by a short last line of at most four words with no sentence in it: a signature,
     * such as "Prof. Martin", "Sarah" or "e.martin@example.edu" (step 13, runs A and B).
     */
    static final Pattern TRAILING_SIGNATURE = Pattern.compile("(?im)(^[ \\t]*(?:best|kind|warm|warmest)?[ \\t]*(?:regards|wishes)"
            + "[ \\t]*[,.!]?[ \\t]*$|^[ \\t]*(?:best|sincerely|thanks|thank you|cordialement|bien (?:a|à) vous|salutations"
            + "|merci|respectueusement)[ \\t]*[,.!]?[ \\t]*$)\\s*\\n[ \\t]*([\\p{L}][\\p{L}.@'\\-]*(?:[ \\t]+[\\p{L}.@'\\-]+){0,3})"
            + "[ \\t]*\\z");

    /** A bracketed placeholder ending the reply, on its own line or after the closing: "[Your Name]", "[Signature]". */
    static final Pattern TRAILING_PLACEHOLDER = Pattern.compile("(?m)(?:^|(?<=,))[ \\t]*(\\[[^\\[\\]\\n]{1,40}\\])\\s*\\z");
    private final LocalLlm llm;
    private final String label;

    public LlmGenerationService(LocalLlm llm) {
        this(llm, "Local model");
    }

    /** @param label how the draft's rationale names the engine, e.g. "Online model" for the development stand-in */
    public LlmGenerationService(LocalLlm llm, String label) {
        this.llm = llm;
        this.label = label;
    }

    @Override
    public ModelRequest prepare(BlackboardView board, AgentConfig agent, ContextSelection context, ExecutionLimits limits) {
        throw new UnsupportedOperationException("Agents build their own model requests");
    }

    @Override
    public GenerationResult generate(ModelRequest request, ModelConfig config) {
        ModelResponse response = llm.complete(request, config);
        if (response.output().isPresent() == response.failure().isPresent()) {
            return failed(new Failure(FailureKind.INVALID_OUTPUT, "A model response must hold output or a failure", false));
        }
        if (response.failure().isPresent()) {
            return failed(response.failure().get());
        }
        String body = response.output().get().strip();
        if (PROMPT_BLOCK.matcher(body).find()) {
            return failed(new Failure(FailureKind.INVALID_OUTPUT, "The model echoed prompt blocks instead of a reply", true));
        }
        String rationale = label + " " + config.model();
        Matcher placeholder = TRAILING_PLACEHOLDER.matcher(body);
        if (placeholder.find()) {
            // Small models sign with "[Your Name]" despite the rule; the person who sends the reply signs it.
            rationale += "; removed the placeholder " + placeholder.group(1).strip();
            body = body.substring(0, placeholder.start()).strip();
            if (body.isEmpty()) {
                return failed(new Failure(FailureKind.INVALID_OUTPUT, "The model returned only a placeholder", true));
            }
        }
        Matcher signature = TRAILING_SIGNATURE.matcher(body);
        if (signature.find()) {
            // Signed despite the rule ("Prof. Martin", the sender's own name, an address): whoever sends it signs.
            rationale += "; removed the signature " + signature.group(2).strip();
            body = body.substring(0, signature.end(1)).strip();
        }
        if (!hasContent(body)) {
            // Only a greeting and a closing are left (step 13, run D: "Best regards," and a name): not a reply.
            return failed(new Failure(FailureKind.INVALID_OUTPUT, "The model returned no reply text, only a greeting or "
                    + "a closing", true));
        }
        return new GenerationResult(Optional.of(new DraftProposal("", body, request.agentId(), rationale)), Optional.empty());
    }

    private static final Pattern GREETING_OR_CLOSING = Pattern.compile("(?i)^(?:(?:dear|hello|hi|bonjour|madame|monsieur|cher|"
            + "chère)\\b[^.!?]{0,40},|(?:best|kind|warm|warmest)?\\s*(?:regards|wishes)[,.!]?|best[,.!]?|sincerely[,.!]?|"
            + "thanks,?|thank you,?|cordialement[,.!]?|bien (?:a|à) vous[,.!]?|salutations[,.!]?|merci,?)$");

    /** True if some line is neither a greeting nor a closing. */
    static boolean hasContent(String body) {
        return body.lines().map(String::strip).filter(line -> !line.isEmpty())
                .anyMatch(line -> !GREETING_OR_CLOSING.matcher(line).matches());
    }

    private static GenerationResult failed(Failure failure) {
        return new GenerationResult(Optional.empty(), Optional.of(failure));
    }
}
