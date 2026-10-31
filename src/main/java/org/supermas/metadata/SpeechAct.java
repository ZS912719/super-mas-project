package org.supermas.metadata;

/**
 * One predicted speech act: request, propose, commit or deliver, as in docs/benchmark/field-schema.md.
 * This is a hint about the email, so it keeps its confidence and source and must never grant anything.
 */
public record SpeechAct(String act, double confidence, String source) { }
