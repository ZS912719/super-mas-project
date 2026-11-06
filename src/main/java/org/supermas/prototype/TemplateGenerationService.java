package org.supermas.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.supermas.blackboard.api.BlackboardView;
import org.supermas.llm.GenerationService;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.ContextSelection;
import org.supermas.model.Domain.DraftProposal;
import org.supermas.model.Domain.ExecutionLimits;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.GenerationResult;
import org.supermas.model.Domain.ModelConfig;
import org.supermas.model.Domain.ModelRequest;

// PROTOTYPE-STAND-IN: owned by the team's LLM integration and final email writing, not by the specialized agents.
// Required by these agents: generate(request, config) returns exactly one of a draft or a failure.
// Replace with the real implementation.
/**
 * Turns a reply plan into a fixed-template draft with no model, so the whole loop can run and be tested. It reads
 * only the plan block of the request: each quoted request gets a placeholder for the reviewer, and missing
 * fields become a question to the sender. It never invents an answer.
 */
public final class TemplateGenerationService implements GenerationService {
    private static final String PLAN_LABEL = "<<REPLY PLAN (JSON)>>\n";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> FIELDS_EN = Map.of("COURSE", "the course", "ID",
            "your student or reference number", "DATE", "the date", "DEADLINE", "the deadline", "PERSON",
            "the person concerned");
    private static final Map<String, String> FIELDS_FR = Map.of("COURSE", "le cours", "ID",
            "votre numéro d'étudiant ou de dossier", "DATE", "la date", "DEADLINE", "la date limite", "PERSON",
            "la personne concernée");

    /** Role agents build their own requests (prompt management is theirs); this stand-in does not. */
    @Override
    public ModelRequest prepare(BlackboardView board, AgentConfig agent, ContextSelection context, ExecutionLimits limits) {
        throw new UnsupportedOperationException("Role agents build their own model requests");
    }

    @Override
    public GenerationResult generate(ModelRequest request, ModelConfig config) {
        Optional<JsonNode> plan = plan(request.untrustedData());
        if (plan.isEmpty()) {
            return new GenerationResult(Optional.empty(),
                    Optional.of(new Failure(FailureKind.INVALID_OUTPUT, "The request holds no readable plan", false)));
        }
        boolean french = "fr".equals(plan.get().path("language").asText());
        StringBuilder body = new StringBuilder(french ? "Bonjour,\n\nMerci pour votre message.\n" : "Hello,\n\nThank you for your message.\n");
        for (JsonNode item : plan.get().path("requests")) {
            body.append(french ? "\nConcernant : « " : "\nRegarding: \"").append(item.asText())
                    .append(french ? " »\n[À compléter par la personne qui relit.]\n" : "\"\n[To be completed by the reviewer.]\n");
        }
        if (plan.get().path("missing").size() > 0) {
            body.append(french ? "\nPourriez-vous m'indiquer " : "\nCould you please send me ");
            int i = 0;
            for (JsonNode field : plan.get().path("missing")) {
                body.append(i++ == 0 ? "" : ", ").append((french ? FIELDS_FR : FIELDS_EN).getOrDefault(field.asText(),
                        field.asText().toLowerCase(java.util.Locale.ROOT)));
            }
            body.append(" ?\n".substring(french ? 0 : 1));
        }
        body.append(french ? "\nCordialement" : "\nBest regards");
        return new GenerationResult(Optional.of(new DraftProposal("", body.toString(), request.agentId(),
                "Template stand-in, no model")), Optional.empty());
    }

    private static Optional<JsonNode> plan(String data) {
        int start = data == null ? -1 : data.indexOf(PLAN_LABEL);
        if (start < 0) {
            return Optional.empty();
        }
        int from = start + PLAN_LABEL.length();
        int end = data.indexOf("\n<<", from);
        try {
            return Optional.of(JSON.readTree(end < 0 ? data.substring(from) : data.substring(from, end)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
