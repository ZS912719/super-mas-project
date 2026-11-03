package org.supermas.agent.role;

import java.util.List;

/** Finds what the sender asks for, as verbatim sentences from the email's new text. */
@FunctionalInterface
public interface RequestExtractor {
    /**
     * @param requests sentences copied from the email, never rewritten
     * @param source which extractor produced them: rules, llm, or rules after a failed LLM call
     */
    record Requests(List<String> requests, String source) {
        public Requests {
            requests = List.copyOf(requests);
        }
    }

    Requests extract(String currentText, String language);
}
