package org.supermas.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.supermas.metadata.GlinerEntityExtractor;
import org.supermas.metadata.GlinerEntityModel;
import org.supermas.metadata.MetadataAgent;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.metadata.OnnxSpeechActClassifier;
import org.supermas.metadata.SpeechActClassifier;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;

/** The real {@link MetadataAgent}, scored end to end. A stage that failed counts as a predictor failure. */
public final class MetadataPredictor implements Predictor {
    private final ObjectMapper json = new ObjectMapper();
    private final MimeEmailParser parser = new MimeEmailParser();
    private final MetadataAgent agent;

    private MetadataPredictor(MetadataAgent agent) {
        this.agent = agent;
    }

    public static Predictor rules() {
        return new MetadataPredictor(MetadataAgent.rulesOnly());
    }

    public static Predictor create(boolean entityModel, boolean actModel) {
        try {
            Optional<GlinerEntityExtractor> entities = entityModel
                    ? Optional.of(new GlinerEntityExtractor(new GlinerEntityModel(
                            Path.of("tmp/models/gliner-multi"), "model_fp16.onnx", 0.5)))
                    : Optional.empty();
            Optional<SpeechActClassifier> acts = actModel
                    ? Optional.of(new OnnxSpeechActClassifier(Path.of("tmp/models/speech-act")))
                    : Optional.empty();
            return new MetadataPredictor(new MetadataAgent(entities, acts));
        } catch (Exception e) {
            throw new IllegalStateException("Model unavailable", e);
        }
    }

    @Override
    public Extraction predict(byte[] rawEmail) throws Exception {
        Email email = parser.parse(rawEmail, "benchmark");
        Analysis analysis = agent.analyze(email);
        if (json.readTree(analysis.metadata().get(MetadataAgent.KEY_FAILED_STAGES)).size() > 0) {
            throw new IllegalStateException("A metadata stage failed");
        }
        List<Extraction.Entity> entities = new ArrayList<>();
        for (JsonNode entity : json.readTree(analysis.metadata().get(MetadataAgent.KEY_ENTITIES))) {
            entities.add(new Extraction.Entity(entity.get("type").asText(), entity.get("text").asText()));
        }
        List<String> acts = new ArrayList<>();
        for (JsonNode act : json.readTree(analysis.metadata().get(MetadataAgent.KEY_SPEECH_ACTS))) {
            acts.add(act.get("act").asText());
        }
        return new Extraction(email.sender(), email.recipients(),
                email.receivedAt() == null ? null : email.receivedAt().toString(), email.scope().threadId(),
                analysis.metadata().get(MetadataAgent.KEY_CURRENT_TEXT), entities, acts,
                analysis.metadata().get(MetadataAgent.KEY_ROLE_HINT), analysis.metadata().get(MetadataAgent.KEY_LANGUAGE));
    }
}
