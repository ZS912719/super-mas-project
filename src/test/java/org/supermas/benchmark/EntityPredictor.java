package org.supermas.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.supermas.metadata.EntityMention;
import org.supermas.metadata.GlinerEntityExtractor;
import org.supermas.metadata.GlinerEntityModel;
import org.supermas.metadata.MetadataAgent;
import org.supermas.metadata.RuleEntityExtractor;

/** Facts plus entities from rules, GLiNER, or both, for comparing extraction variants. */
public final class EntityPredictor implements Predictor {
    private final FactsPredictor facts = new FactsPredictor();
    private final RuleEntityExtractor rules;
    private final GlinerEntityExtractor gliner;
    private boolean hybrid;

    private EntityPredictor(RuleEntityExtractor rules, GlinerEntityExtractor gliner) {
        this.rules = rules;
        this.gliner = gliner;
    }

    /** Each source only for the types where it was stronger on dev: see docs/reports/step-1.6.md. */
    public static Predictor hybrid() {
        EntityPredictor predictor = (EntityPredictor) gliner(true);
        predictor.hybrid = true;
        return predictor;
    }

    public static Predictor rulesOnly() {
        return new EntityPredictor(new RuleEntityExtractor(), null);
    }

    public static Predictor gliner(boolean withRules) {
        try {
            double threshold = Double.parseDouble(System.getProperty("gliner.threshold", "0.5"));
            GlinerEntityModel model = new GlinerEntityModel(
                    Path.of("tmp/models/gliner-multi"), "model_fp16.onnx", threshold);
            return new EntityPredictor(withRules ? new RuleEntityExtractor() : null,
                    new GlinerEntityExtractor(model));
        } catch (Exception e) {
            throw new IllegalStateException("GLiNER model unavailable", e);
        }
    }

    @Override
    public Extraction predict(byte[] rawEmail) throws Exception {
        Extraction base = facts.predict(rawEmail);
        Map<String, Extraction.Entity> entities = new LinkedHashMap<>();
        List<EntityMention> fromModel = gliner == null ? List.of() : gliner.extract(base.currentText());
        List<EntityMention> fromRules = rules == null ? List.of() : rules.extract(base.currentText());
        List<EntityMention> mentions = new ArrayList<>();
        if (hybrid) {
            mentions.addAll(MetadataAgent.hybrid(fromModel, fromRules));
        } else {
            // The model goes first, so it decides the type when both sources find the same text.
            mentions.addAll(fromModel);
            mentions.addAll(fromRules);
        }
        for (EntityMention mention : mentions) {
            entities.putIfAbsent(mention.text().toLowerCase(Locale.ROOT),
                    new Extraction.Entity(mention.type(), mention.text()));
        }
        return new Extraction(base.sender(), base.recipients(), base.date(), base.threadId(),
                base.currentText(), new ArrayList<>(entities.values()), base.speechActs(),
                base.roleHint(), base.language());
    }
}
