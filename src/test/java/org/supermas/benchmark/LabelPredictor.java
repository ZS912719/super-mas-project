package org.supermas.benchmark;

import java.nio.file.Path;
import org.supermas.metadata.OnnxSpeechActClassifier;
import org.supermas.metadata.RuleRoleHintClassifier;
import org.supermas.metadata.RuleSpeechActClassifier;
import org.supermas.metadata.SpeechAct;
import org.supermas.metadata.SpeechActClassifier;

/** Facts plus speech acts. On data the model was trained on, the model variant's scores are inflated. */
public final class LabelPredictor implements Predictor {
    private final FactsPredictor facts = new FactsPredictor();
    private final SpeechActClassifier classifier;
    private final RuleRoleHintClassifier roles = new RuleRoleHintClassifier();

    private LabelPredictor(SpeechActClassifier classifier) {
        this.classifier = classifier;
    }

    public static Predictor rules() {
        return new LabelPredictor(new RuleSpeechActClassifier());
    }

    public static Predictor model() {
        try {
            return new LabelPredictor(new OnnxSpeechActClassifier(Path.of("tmp/models/speech-act")));
        } catch (Exception e) {
            throw new IllegalStateException("Speech-act model unavailable", e);
        }
    }

    @Override
    public Extraction predict(byte[] rawEmail) throws Exception {
        Extraction base = facts.predict(rawEmail);
        return new Extraction(base.sender(), base.recipients(), base.date(), base.threadId(), base.currentText(),
                base.entities(), classifier.classify(base.currentText()).stream().map(SpeechAct::act).toList(),
                roles.classify(base.currentText()).orElse(null), base.language());
    }
}
