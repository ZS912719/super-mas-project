package org.supermas.metadata;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the model exported by scripts/training/train_setfit.py: a sentence encoder in ONNX, then one
 * linear classifier per act from head.json. See docs/reports/step-1.8.md.
 *
 * <p>The input text is untrusted, but it only ever reaches this model as data to classify.
 */
public final class OnnxSpeechActClassifier implements SpeechActClassifier, AutoCloseable {
    private static final String SOURCE = "onnx-speech-act";

    private record Head(String act, float[] coefficients, double intercept) { }

    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final List<Head> heads = new ArrayList<>();

    public OnnxSpeechActClassifier(Path directory) throws OrtException, IOException {
        JsonNode head = new ObjectMapper().readTree(directory.resolve("head.json").toFile());
        for (int i = 0; i < head.get("acts").size(); i++) {
            JsonNode classifier = head.get("classifiers").get(i);
            float[] coefficients = new float[classifier.get("coefficients").size()];
            for (int c = 0; c < coefficients.length; c++) {
                coefficients[c] = (float) classifier.get("coefficients").get(c).asDouble();
            }
            heads.add(new Head(head.get("acts").get(i).asText(), coefficients, classifier.get("intercept").asDouble()));
        }
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        this.session = environment.createSession(directory.resolve("model.onnx").toString(), options);
        this.tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerPath(directory.resolve("tokenizer.json"))
                .optTruncation(true)
                .optMaxLength(head.get("maxTokens").asInt())
                .optPadding(false)
                .build();
    }

    @Override
    public List<SpeechAct> classify(String currentText) throws OrtException {
        List<SpeechAct> acts = new ArrayList<>();
        if (currentText == null || currentText.isBlank()) {
            return acts;  // The classifier was trained only on emails with new text.
        }
        float[] embedding = embed(currentText);
        for (Head head : heads) {
            double score = head.intercept();
            for (int i = 0; i < embedding.length; i++) {
                score += embedding[i] * head.coefficients()[i];
            }
            if (score > 0) {
                acts.add(new SpeechAct(head.act(), 1.0 / (1.0 + Math.exp(-score)), SOURCE));
            }
        }
        return acts;
    }

    float[] embed(String text) throws OrtException {
        Encoding encoding = tokenizer.encode(text);
        try (OnnxTensor ids = OnnxTensor.createTensor(environment, new long[][] {encoding.getIds()});
             OnnxTensor mask = OnnxTensor.createTensor(environment, new long[][] {encoding.getAttentionMask()});
             OrtSession.Result result = session.run(Map.of("input_ids", ids, "attention_mask", mask))) {
            return ((float[][]) result.get("embedding").orElseThrow().getValue())[0];
        }
    }

    @Override
    public void close() throws OrtException {
        tokenizer.close();
        session.close();
    }
}
