package org.supermas.benchmark;

import java.util.List;
import org.supermas.metadata.LanguageDetector;
import org.supermas.metadata.MimeEmailParser;
import org.supermas.metadata.QuoteSignatureStripper;
import org.supermas.model.Domain.Email;

/**
 * The no-model metadata pipeline as a benchmark variant: parser, quote/signature stripper and
 * language detector. It fills only the fields these produce. Entities, speech acts and role are
 * left empty, because those need the models from later steps.
 *
 * <p>{@code threadId} is the parser's provisional value (first reference, else own id). The real
 * thread id needs the whole mailbox, so threading accuracy is measured separately (step 1.3), not
 * by this per-email predictor.
 */
public final class FactsPredictor implements Predictor {
    private final MimeEmailParser parser = new MimeEmailParser();
    private final QuoteSignatureStripper stripper = new QuoteSignatureStripper();
    private final LanguageDetector detector = new LanguageDetector();

    @Override
    public Extraction predict(byte[] rawEmail) throws Exception {
        Email email = parser.parse(rawEmail, "benchmark");
        String currentText = stripper.currentText(email.body());
        return new Extraction(email.sender(), email.recipients(),
                email.receivedAt() == null ? null : email.receivedAt().toString(),
                email.scope().threadId(), currentText, List.of(), List.of(), null,
                detector.detect(currentText));
    }
}
