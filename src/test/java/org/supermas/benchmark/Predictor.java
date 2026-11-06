package org.supermas.benchmark;

/** One benchmark variant. It receives the raw email only and never sees the gold labels. */
@FunctionalInterface
public interface Predictor {
    Extraction predict(byte[] rawEmail) throws Exception;
}
