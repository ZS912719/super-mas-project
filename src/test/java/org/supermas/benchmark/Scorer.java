package org.supermas.benchmark;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Scores predictions per field, using the rules in docs/benchmark/field-schema.md. */
public final class Scorer {
    private static final Map<String, Function<Extraction, List<String>>> FIELDS = fields();

    private Scorer() { }

    public record Pair(Extraction gold, Extraction predicted) { }

    /** Precision, recall and null accuracy are null when undefined, such as with no predictions. */
    public record FieldScore(int truePositives, int falsePositives, int falseNegatives,
                             int emptyGoldEmails, int emptyGoldCorrect) {
        public int goldCount() {
            return truePositives + falseNegatives;
        }

        public Double precision() {
            int predicted = truePositives + falsePositives;
            return predicted == 0 ? null : (double) truePositives / predicted;
        }

        public Double recall() {
            return goldCount() == 0 ? null : (double) truePositives / goldCount();
        }

        public Double f1() {
            int denominator = 2 * truePositives + falsePositives + falseNegatives;
            return denominator == 0 ? null : (double) (2 * truePositives) / denominator;
        }

        public Double nullAccuracy() {
            return emptyGoldEmails == 0 ? null : (double) emptyGoldCorrect / emptyGoldEmails;
        }
    }

    public static Map<String, FieldScore> score(Collection<Pair> pairs) {
        Map<String, FieldScore> scores = new LinkedHashMap<>();
        FIELDS.forEach((name, extractor) -> {
            int tp = 0, fp = 0, fn = 0, emptyGold = 0, emptyCorrect = 0;
            for (Pair pair : pairs) {
                List<String> gold = normalizeAll(extractor.apply(pair.gold()));
                List<String> predicted = normalizeAll(extractor.apply(pair.predicted()));
                int matches = countMatches(gold, predicted);
                tp += matches;
                fp += predicted.size() - matches;
                fn += gold.size() - matches;
                if (gold.isEmpty()) {
                    emptyGold++;
                    if (predicted.isEmpty()) {
                        emptyCorrect++;
                    }
                }
            }
            scores.put(name, new FieldScore(tp, fp, fn, emptyGold, emptyCorrect));
        });
        return scores;
    }

    /** Averages over fields with gold values; null if no field has any. */
    public static Double macroF1(Map<String, FieldScore> scores) {
        return scores.values().stream()
                .filter(score -> score.goldCount() > 0)
                .mapToDouble(FieldScore::f1)
                .average()
                .stream().boxed().findFirst().orElse(null);
    }

    static String normalize(String text) {
        String composed = Normalizer.normalize(text, Normalizer.Form.NFC);
        return composed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static List<String> normalizeAll(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String text = normalize(value);
            if (!text.isEmpty()) {
                normalized.add(text);
            }
        }
        return normalized;
    }

    private static int countMatches(List<String> gold, List<String> predicted) {
        Map<String, Integer> remaining = new HashMap<>();
        gold.forEach(value -> remaining.merge(value, 1, Integer::sum));
        int matches = 0;
        for (String value : predicted) {
            int count = remaining.getOrDefault(value, 0);
            if (count > 0) {
                remaining.put(value, count - 1);
                matches++;
            }
        }
        return matches;
    }

    private static Map<String, Function<Extraction, List<String>>> fields() {
        Map<String, Function<Extraction, List<String>>> fields = new LinkedHashMap<>();
        fields.put("sender", e -> single(e.sender()));
        fields.put("recipients", Extraction::recipients);
        fields.put("date", e -> single(e.date()));
        fields.put("threadId", e -> single(e.threadId()));
        fields.put("currentText", e -> single(e.currentText()));
        for (String type : List.of("PERSON", "COURSE", "DATE", "DEADLINE", "ID")) {
            fields.put("entity:" + type, e -> e.entities().stream()
                    .filter(entity -> entity.type().equals(type))
                    .map(Extraction.Entity::text)
                    .toList());
        }
        fields.put("speechActs", Extraction::speechActs);
        fields.put("roleHint", e -> single(e.roleHint()));
        fields.put("language", e -> single(e.language()));
        return fields;
    }

    private static List<String> single(String value) {
        return value == null ? List.of() : List.of(value);
    }
}
