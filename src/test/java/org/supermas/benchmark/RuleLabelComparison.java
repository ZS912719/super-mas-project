package org.supermas.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.supermas.metadata.RuleSpeechActClassifier;
import org.supermas.metadata.SpeechAct;

/**
 * Scores the speech-act rules on the same text the models saw in cross-validation (labelled
 * currentText, skipping empty text), so the result compares directly with the models' cv.json.
 * Prints counts only, never email text. Usage: RuleLabelComparison DEV_DIR
 */
public final class RuleLabelComparison {
    private static final List<String> ACTS = List.of("request", "propose", "commit", "deliver");

    private RuleLabelComparison() { }

    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper();
        RuleSpeechActClassifier rules = new RuleSpeechActClassifier();
        int[][] counts = new int[ACTS.size()][3];
        int used = 0;
        Set<Path> labels = new TreeSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(Path.of(args[0]), "*.eml")) {
            files.forEach(labels::add);
        }
        for (Path eml : labels) {
            String stem = eml.getFileName().toString().replaceAll("\\.eml$", "");
            Path gold = eml.resolveSibling(stem + ".gold.json");
            JsonNode label = json.readTree((Files.exists(gold) ? gold : eml.resolveSibling(stem + ".gold.draft.json")).toFile());
            String text = label.get("currentText").asText();
            if (text.isBlank()) {
                continue;
            }
            used++;
            Set<String> truth = new HashSet<>();
            label.get("speechActs").forEach(act -> truth.add(act.asText()));
            Set<String> predicted = new HashSet<>();
            rules.classify(text).stream().map(SpeechAct::act).forEach(predicted::add);
            for (int i = 0; i < ACTS.size(); i++) {
                boolean t = truth.contains(ACTS.get(i));
                boolean p = predicted.contains(ACTS.get(i));
                counts[i][0] += t && p ? 1 : 0;
                counts[i][1] += !t && p ? 1 : 0;
                counts[i][2] += t && !p ? 1 : 0;
            }
        }
        double macro = 0;
        System.out.println("emails " + used);
        for (int i = 0; i < ACTS.size(); i++) {
            double f1 = counts[i][0] == 0 ? 0 : 2.0 * counts[i][0] / (2 * counts[i][0] + counts[i][1] + counts[i][2]);
            macro += f1 / ACTS.size();
            System.out.printf(Locale.ROOT, "%s tp=%d fp=%d fn=%d f1=%.3f%n", ACTS.get(i), counts[i][0], counts[i][1], counts[i][2], f1);
        }
        System.out.printf(Locale.ROOT, "macroF1=%.3f%n", macro);
    }
}
