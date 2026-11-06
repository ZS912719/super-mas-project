package org.supermas.context;

import java.util.Set;

/**
 * What an email seems to need but does not state. This is a hint computed from untrusted text. It can
 * ask for more retrieval within budget, and it can never grant anything.
 *
 * @param fields the fields reported as missing
 * @param source which check produced this: rules, llm, or rules after a failed LLM call
 */
public record MissingInformation(Set<InformationField> fields, String source) {
    public MissingInformation {
        fields = Set.copyOf(fields);
    }
}
