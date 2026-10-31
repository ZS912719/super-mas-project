package org.supermas.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.supermas.llm.LocalLlm;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.ModelConfig;
import org.supermas.model.Domain.ModelRequest;
import org.supermas.model.Domain.ModelResponse;

/**
 * An optional alternative to the rules, off unless someone chooses to build it. The model receives only
 * labels from fixed lists: field names and whether each is present, speech acts, and table row names. No
 * email text is sent, so a value, name or instruction in the email cannot reach it. Any failure or unknown
 * answer falls back to the rules, and the result's source says so.
 */
public final class LlmMissingInformationCheck implements MissingInformationCheck {
    public static final String SOURCE = "llm";
    public static final String FALLBACK_SOURCE = "rules-after-llm-failure";
    private static final String AGENT_ID = "missing-information";
    private static final int MAX_INPUT_CHARACTERS = 1000;
    private static final Set<String> KNOWN_ACTS = Set.of("request", "propose", "commit", "deliver");
    private static final String SYSTEM_PROMPT = """
            You decide which information an email is missing, given only a summary of it.
            "present" says which fields the email states. "speechActs" says what the email does.
            "mentions" says what the email is about.
            Answer with JSON only: {"missing": [...]}, using only the names in "present" whose value is false.
            Use an empty list when nothing is needed.
            """;

    private static final ObjectMapper JSON = new ObjectMapper();
    private final LocalLlm llm;
    private final ModelConfig config;
    private final RuleMissingInformationCheck rules = new RuleMissingInformationCheck();

    public LlmMissingInformationCheck(LocalLlm llm, ModelConfig config) {
        this.llm = llm;
        this.config = config;
    }

    @Override
    public MissingInformation check(Email email, Analysis analysis) {
        try {
            String data = summary(email, analysis);
            ModelResponse response = llm.complete(
                    new ModelRequest(AGENT_ID, SYSTEM_PROMPT, data, MAX_INPUT_CHARACTERS), config);
            Optional<Set<InformationField>> parsed = response.output().flatMap(LlmMissingInformationCheck::parse);
            if (parsed.isPresent()) {
                return new MissingInformation(parsed.get(), SOURCE);
            }
        } catch (RuntimeException | JsonProcessingException e) {
            // Falls through to the rules: the source below records that this happened.
        }
        return new MissingInformation(rules.check(email, analysis).fields(), FALLBACK_SOURCE);
    }

    private String summary(Email email, Analysis analysis) throws JsonProcessingException {
        Set<String> acts = new TreeSet<>(RuleMissingInformationCheck.acts(analysis));
        acts.retainAll(KNOWN_ACTS);  // The metadata is built from email text, so only known labels pass.
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("present", rules.presence(email, analysis));
        summary.put("speechActs", acts);
        summary.put("mentions", RuleMissingInformationCheck.mentions(email, analysis));
        return JSON.writeValueAsString(summary);
    }

    /** Empty unless the whole answer is valid: one unknown name rejects it, because the model has gone off script. */
    private static Optional<Set<InformationField>> parse(String output) {
        try {
            JsonNode missing = JSON.readTree(output).path("missing");
            if (!missing.isArray()) {
                return Optional.empty();
            }
            Set<InformationField> fields = EnumSet.noneOf(InformationField.class);
            for (JsonNode name : missing) {
                fields.add(InformationField.valueOf(name.asText()));
            }
            return Optional.of(fields);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
