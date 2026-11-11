package org.supermas.agent.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.supermas.metadata.LanguageDetector;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.metadata.QuoteSignatureStripper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.supermas.model.Domain.ContextSelection;
import static org.supermas.model.Domain.Email;
import static org.supermas.model.Domain.Evidence;
import static org.supermas.model.Domain.EvidenceKind;
import static org.supermas.model.Domain.Provenance;
import static org.supermas.model.Domain.Scope;

/**
 * Benchmark plan C11: does {@link ExcerptRelevance} keep the knowledge a reply needs and drop the rest?
 *
 * <p>Every reply-expected synthetic email is paired with every knowledge document. A pair is <em>relevant</em> when
 * the document holds one of the email's gold facts ({@code mustMention} or {@code mustAsk} terms of its frozen reply
 * checks), and <em>noise</em> when it holds a term of the email's {@code noise} group of {@code mustNotContain}.
 * The filter sees the email's extracted requests (rules) and its subject, an upper bound of what one specialist sees
 * (it sees only the requests it answers). Writes counts only, no email text.
 *
 * <p>Usage: {@code ExcerptRelevanceBenchmark KNOWLEDGE_DIR REPORT_FILE DATA_DIR...}
 */
public final class ExcerptRelevanceBenchmark {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LanguageDetector LANGUAGES = new LanguageDetector();

    private ExcerptRelevanceBenchmark() { }

    record Doc(String id, String text) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: ExcerptRelevanceBenchmark KNOWLEDGE_DIR REPORT_FILE DATA_DIR...");
        }
        List<Doc> docs = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of(args[0]))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                docs.add(new Doc(file.getParent().getFileName() + "/" + file.getFileName(), Files.readString(file)));
            }
        }
        // counts[language][0..5]: relevant, relevant kept, other, other dropped, noise, noise dropped
        int[][] counts = new int[2][6];
        int emails = 0;
        MimeEmailParser parser = new MimeEmailParser();
        QuoteSignatureStripper stripper = new QuoteSignatureStripper();
        RuleRequestExtractor extractor = new RuleRequestExtractor();
        for (int a = 2; a < args.length; a++) {
            try (Stream<Path> files = Files.list(Path.of(args[a]))) {
                for (Path checks : files.filter(p -> p.toString().endsWith(".reply.json")).sorted().toList()) {
                    JsonNode gold = JSON.readTree(checks.toFile());
                    if (!gold.path("replyExpected").asBoolean()) {
                        continue;
                    }
                    String stem = checks.getFileName().toString().replace(".reply.json", "");
                    Email email = parser.parse(Files.readAllBytes(checks.resolveSibling(stem + ".eml")), "uni");
                    String text = stripper.currentText(email.body());
                    String language = gold.path("language").asText("en");
                    List<String> about = new ArrayList<>(extractor.extract(text, language).requests());
                    about.add(email.subject() == null ? "" : email.subject());
                    List<String> facts = terms(gold.path("mustMention"), null);
                    facts.addAll(terms(gold.path("mustAsk"), null));
                    List<String> noise = terms(gold.path("mustNotContain"), "noise");
                    emails++;
                    int lang = "fr".equals(language) ? 1 : 0;
                    for (Doc doc : docs) {
                        Evidence item = new Evidence(stem + ":" + doc.id(), new Scope("uni", stem), EvidenceKind.RETRIEVED_EVIDENCE,
                                new Provenance(doc.id(), "From knowledge source " + doc.id(), Instant.EPOCH), doc.text(), Set.of());
                        ContextSelection kept = ExcerptRelevance.related(new ContextSelection(List.of(item), doc.text().length(),
                                List.of()), about, new ArrayList<>());
                        boolean keptIt = !kept.evidence().isEmpty();
                        boolean relevant = containsAny(doc.text(), facts);
                        boolean isNoise = !relevant && containsAny(doc.text(), noise);
                        if (relevant) {
                            counts[lang][0]++;
                            counts[lang][1] += keptIt ? 1 : 0;
                        } else {
                            counts[lang][2]++;
                            counts[lang][3] += keptIt ? 0 : 1;
                            if (isNoise) {
                                counts[lang][4]++;
                                counts[lang][5] += keptIt ? 0 : 1;
                            }
                        }
                    }
                }
            }
        }
        StringBuilder out = new StringBuilder("# Excerpt Relevance (benchmark plan C11)\n\n")
                .append("Run ").append(Instant.now().toString(), 0, 16).append("Z. ").append(emails)
                .append(" reply-expected synthetic emails × ").append(docs.size()).append(" knowledge documents = ")
                .append(emails * docs.size()).append(" pairs. Relevant: the document holds a gold fact of the email (`mustMention`, ")
                .append("`mustAsk`); noise: it holds a term of the email's `noise` check. The filter sees all extracted requests ")
                .append("and the subject (an upper bound of one specialist's view). Knowledge documents are in English and the filter ")
                .append("keeps excerpts in another language than the requests, so it can hardly judge French emails.\n\n")
                .append("| Email language | Relevant pairs | Relevant kept (recall) | Other pairs | Other dropped | Noise pairs | Noise dropped |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        String[] names = {"English", "French"};
        for (int lang = 0; lang < 2; lang++) {
            int[] c = counts[lang];
            out.append("| ").append(names[lang]).append(" | ").append(c[0]).append(" | ").append(ratio(c[1], c[0]))
                    .append(" | ").append(c[2]).append(" | ").append(ratio(c[3], c[2])).append(" | ").append(c[4])
                    .append(" | ").append(ratio(c[5], c[4])).append(" |\n");
        }
        Files.writeString(Path.of(args[1]), out.toString());
        System.out.print(out);
    }

    private static List<String> terms(JsonNode groups, String onlyId) {
        List<String> terms = new ArrayList<>();
        for (JsonNode group : groups) {
            if (onlyId == null || onlyId.equals(group.path("id").asText())) {
                group.path("any").forEach(term -> terms.add(term.asText()));
            }
        }
        return terms;
    }

    private static boolean containsAny(String text, List<String> terms) {
        String haystack = fold(text);
        return terms.stream().map(ExcerptRelevanceBenchmark::fold).anyMatch(t -> !t.isBlank() && haystack.contains(t));
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('’', '\'')
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String ratio(int part, int whole) {
        return whole == 0 ? "n/a" : part + " / " + whole + String.format(Locale.ROOT, " (%.2f)", (double) part / whole);
    }
}
