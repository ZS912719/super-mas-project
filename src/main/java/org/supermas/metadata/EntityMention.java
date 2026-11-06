package org.supermas.metadata;

/**
 * One extracted entity. Types follow docs/benchmark/field-schema.md: PERSON, COURSE, DATE, DEADLINE, ID.
 * This is a model or rule output, never a verified fact, so it keeps its confidence and source.
 */
public record EntityMention(String type, String text, double confidence, String source) { }
