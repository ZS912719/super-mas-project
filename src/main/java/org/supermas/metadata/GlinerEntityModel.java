package org.supermas.metadata;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs an ONNX export of a GLiNER span model in-process, reproducing the Python library's input
 * format and decoding: a prompt of {@code <<ENT>> label ... <<SEP>>}, then the text's words, with
 * logits per (word, span width, label). See docs/reports/step-1.5.md.
 *
 * <p>The input text is untrusted, but it only ever reaches this model as data to tag.
 */
public final class GlinerEntityModel implements AutoCloseable {
    public record Span(String label, String text, double score, int start, int end) { }

    private static final Pattern WORD = Pattern.compile("\\w+(?:[-_]\\w+)*|\\S",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final int MAX_WIDTH = 12;
    private static final int MAX_TOKENS = 380;
    private static final long CLS = 1;
    private static final long SEP = 2;
    private static final String ENT_TOKEN = "<<ENT>>";
    private static final String SEP_TOKEN = "<<SEP>>";

    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final double threshold;

    public GlinerEntityModel(Path directory, String modelFile, double threshold)
            throws OrtException, IOException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        this.session = environment.createSession(directory.resolve(modelFile).toString(), options);
        this.tokenizer = HuggingFaceTokenizer.newInstance(directory.resolve("tokenizer.json"));
        this.threshold = threshold;
    }

    private record Word(String text, int start, int end) { }

    /** Non-overlapping spans above the threshold, in text order. Long text is split into chunks. */
    public List<Span> predict(String text, List<String> labels) throws OrtException {
        List<Long> prompt = new ArrayList<>();
        prompt.add(CLS);
        for (String label : labels) {
            addAll(prompt, tokenizer.encode(ENT_TOKEN, false, false).getIds());
            addAll(prompt, tokenizer.encode(label, false, false).getIds());
        }
        addAll(prompt, tokenizer.encode(SEP_TOKEN, false, false).getIds());

        List<Span> spans = new ArrayList<>();
        List<Word> chunk = new ArrayList<>();
        List<long[]> chunkTokens = new ArrayList<>();
        int size = prompt.size() + 1;
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            Word word = new Word(matcher.group(), matcher.start(), matcher.end());
            long[] ids = tokenizer.encode(word.text(), false, false).getIds();
            if (size + ids.length > MAX_TOKENS && !chunk.isEmpty()) {
                spans.addAll(run(text, prompt, chunk, chunkTokens, labels));
                chunk = new ArrayList<>();
                chunkTokens = new ArrayList<>();
                size = prompt.size() + 1;
            }
            chunk.add(word);
            chunkTokens.add(ids);
            size += ids.length;
        }
        if (!chunk.isEmpty()) {
            spans.addAll(run(text, prompt, chunk, chunkTokens, labels));
        }
        spans.sort(Comparator.comparingInt(Span::start));
        return spans;
    }

    private List<Span> run(String text, List<Long> prompt, List<Word> words, List<long[]> tokens,
                           List<String> labels) throws OrtException {
        List<Long> ids = new ArrayList<>(prompt);
        List<Long> wordsMask = new ArrayList<>();
        prompt.forEach(id -> wordsMask.add(0L));
        for (int w = 0; w < tokens.size(); w++) {
            long[] subtokens = tokens.get(w);
            for (int s = 0; s < subtokens.length; s++) {
                ids.add(subtokens[s]);
                wordsMask.add(s == 0 ? w + 1L : 0L);
            }
        }
        ids.add(SEP);
        wordsMask.add(0L);

        int length = ids.size();
        long[][] inputIds = {toArray(ids)};
        long[][] attention = new long[1][length];
        java.util.Arrays.fill(attention[0], 1L);
        long[][] mask = {toArray(wordsMask)};
        long[][] textLengths = {{words.size()}};
        long[][][] spanIndex = new long[1][words.size() * MAX_WIDTH][2];
        boolean[][] spanMask = new boolean[1][words.size() * MAX_WIDTH];
        for (int start = 0; start < words.size(); start++) {
            for (int width = 0; width < MAX_WIDTH; width++) {
                int slot = start * MAX_WIDTH + width;
                if (start + width < words.size()) {
                    spanIndex[0][slot][0] = start;
                    spanIndex[0][slot][1] = start + width;
                    spanMask[0][slot] = true;
                }
            }
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(environment, inputIds));
            inputs.put("attention_mask", OnnxTensor.createTensor(environment, attention));
            inputs.put("words_mask", OnnxTensor.createTensor(environment, mask));
            inputs.put("text_lengths", OnnxTensor.createTensor(environment, textLengths));
            inputs.put("span_idx", OnnxTensor.createTensor(environment, spanIndex));
            inputs.put("span_mask", OnnxTensor.createTensor(environment, spanMask));
            try (OrtSession.Result result = session.run(inputs)) {
                float[][][][] logits = (float[][][][]) result.get("logits").orElseThrow().getValue();
                return decode(text, words, logits[0], labels);
            }
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    /** Greedy flat decoding: the highest-scoring span wins, and overlapping spans are dropped. */
    private List<Span> decode(String text, List<Word> words, float[][][] logits, List<String> labels) {
        List<Span> candidates = new ArrayList<>();
        for (int start = 0; start < words.size(); start++) {
            for (int width = 0; width < MAX_WIDTH && start + width < words.size(); width++) {
                for (int label = 0; label < labels.size(); label++) {
                    double score = 1.0 / (1.0 + Math.exp(-logits[start][width][label]));
                    if (score > threshold) {
                        int from = words.get(start).start();
                        int to = words.get(start + width).end();
                        candidates.add(new Span(labels.get(label), text.substring(from, to), score, from, to));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Span::score).reversed());
        List<Span> kept = new ArrayList<>();
        for (Span candidate : candidates) {
            if (kept.stream().noneMatch(k -> k.start() < candidate.end() && candidate.start() < k.end())) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static void addAll(List<Long> target, long[] values) {
        for (long value : values) {
            target.add(value);
        }
    }

    private static long[] toArray(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).toArray();
    }

    @Override
    public void close() throws OrtException {
        tokenizer.close();
        session.close();
    }
}
