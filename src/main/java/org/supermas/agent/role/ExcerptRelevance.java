package org.supermas.agent.role;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.supermas.metadata.LanguageDetector;
import org.supermas.model.Domain.ContextSelection;
import org.supermas.model.Domain.Evidence;

/**
 * Keeps only the excerpts that share a word stem with the requests a specialist answers. A search returns its best
 * hits even when none is about the request; passed on, such excerpts end up in the reply (step 13: room booking
 * and resits in a reply about an extension) or get merged into false statements. Stems come from Lucene's English
 * or French analyzer, by the text's language (stop words removed), so "extension" matches "extensions" and "enrolment" matches
 * "Enrolment certificates". Messages of the email's own thread are always kept: they are the conversation itself.
 * An excerpt in another language than the requests is kept too, because shared words cannot judge it.
 *
 * <p>Deliberately lenient (one shared stem is enough), because a missing fact is worse than a loose one; it only
 * removes excerpts with nothing in common with any request.
 */
final class ExcerptRelevance {
    /** Provenance of thread history written by the retriever. */
    private static final String THREAD_MESSAGE = "Thread message";
    private static final LanguageDetector LANGUAGES = new LanguageDetector();
    private static final Analyzer ENGLISH = new EnglishAnalyzer();
    private static final Analyzer FRENCH = new FrenchAnalyzer();
    /** Words of asking, and words common to all university mail, that say nothing about the subject (stem forms). */
    private static final Set<String> ASKING = Set.of("could", "would", "can", "have", "pleas", "please", "like", "also",
            "need", "want", "know", "you", "your", "our", "get", "make", "let", "thank", "thanks", "hello", "dear", "pouv",
            "pourr", "pourriez", "pouvez", "merci", "vous", "votr", "votre", "bonjour", "fair", "faire", "est", "ser", "serait",
            // Too general in a university mailbox to say what a request is about.
            "offic", "office", "bureau", "student", "etudi", "etudiant", "univers", "universit", "staff", "depart",
            "faculti", "faculty", "facult");

    private ExcerptRelevance() { }

    /** The selection without excerpts unrelated to {@code requests}; a note says how many were left out. */
    static ContextSelection related(ContextSelection selection, List<String> requests, List<String> notes) {
        String joined = String.join(" ", requests);
        String language = LANGUAGES.detect(joined);
        Set<String> wanted = stems(joined, language);
        // Requests without a meaningful word ("9", "Here is all the contact info") give no reason to quote any
        // knowledge: only the thread history is kept (W9 on real mail, protocol B51: such an email was answered with
        // the office hours and the facilities portal, because every excerpt used to be kept).
        List<Evidence> kept = wanted.isEmpty()
                ? selection.evidence().stream().filter(ExcerptRelevance::ownThread).toList()
                // Shared stems cannot judge an excerpt in another language (a French email, English knowledge): kept.
                : selection.evidence().stream().filter(item -> ownThread(item)
                        || !LANGUAGES.detect(item.text()).equals(language)
                        || stems(item.text(), language).stream().anyMatch(wanted::contains)).toList();
        if (kept.size() == selection.evidence().size()) {
            return selection;
        }
        notes.add((selection.evidence().size() - kept.size()) + " excerpts unrelated to the requests were left out");
        return new ContextSelection(kept, kept.stream().mapToInt(item -> item.text().length()).sum(),
                selection.omittedSourceIds());
    }

    private static boolean ownThread(Evidence item) {
        return item.provenance() != null && item.provenance().description() != null
                && item.provenance().description().startsWith(THREAD_MESSAGE);
    }

    /**
     * Stems of at least three characters that are not numbers, by the analyzer of {@code language} only: the
     * French analyzer keeps English words such as "the" as stems, which would match everything.
     */
    static Set<String> stems(String text, String language) {
        Set<String> stems = new HashSet<>();
        Analyzer analyzer = "fr".equals(language) ? FRENCH : ENGLISH;
        {
            try (TokenStream tokens = analyzer.tokenStream("text", text == null ? "" : text)) {
                CharTermAttribute term = tokens.addAttribute(CharTermAttribute.class);
                tokens.reset();
                while (tokens.incrementToken()) {
                    String stem = term.toString();
                    if (stem.length() >= 3 && !stem.chars().allMatch(Character::isDigit) && !ASKING.contains(stem)) {
                        stems.add(stem);
                    }
                }
                tokens.end();
            } catch (IOException e) {
                throw new UncheckedIOException("Analyzing text in memory failed", e);
            }
        }
        return stems;
    }
}
