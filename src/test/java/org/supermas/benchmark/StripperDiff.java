package org.supermas.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.metadata.QuoteSignatureStripper;

/**
 * Prints where the stripper disagrees with labelled drafts, to guide rule work. Dev data only.
 * Usage: StripperDiff DEV_DIRECTORY
 */
public final class StripperDiff {
    private StripperDiff() { }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        MimeEmailParser parser = new MimeEmailParser();
        QuoteSignatureStripper stripper = new QuoteSignatureStripper();
        List<Path> drafts;
        try (Stream<Path> files = Files.list(Path.of(args[0]))) {
            drafts = files.filter(p -> p.getFileName().toString().endsWith(".gold.draft.json")).sorted().toList();
        }
        int same = 0;
        for (Path draft : drafts) {
            JsonNode gold = mapper.readTree(draft.toFile());
            String stem = draft.getFileName().toString().replace(".gold.draft.json", "");
            byte[] raw = Files.readAllBytes(draft.resolveSibling(stem + ".eml"));
            String predicted = normalize(stripper.currentText(parser.parse(raw, "dev").body()));
            String expected = normalize(gold.get("currentText").asText());
            if (predicted.equals(expected)) {
                same++;
                continue;
            }
            int common = 0;
            while (common < predicted.length() && common < expected.length()
                    && predicted.charAt(common) == expected.charAt(common)) {
                common++;
            }
            System.out.printf("%s lengths gold=%d pred=%d%n  gold>> %s%n  pred>> %s%n", stem.substring(6, 11),
                    expected.length(), predicted.length(), excerpt(expected, common), excerpt(predicted, common));
        }
        System.out.printf("exact: %d of %d%n", same, drafts.size());
    }

    private static String normalize(String text) {
        return Scorer.normalize(text);
    }

    private static String excerpt(String text, int from) {
        int start = Math.max(0, from - 25);
        return text.substring(start, Math.min(text.length(), from + 70));
    }
}
