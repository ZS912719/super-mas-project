package org.supermas.context;

import org.supermas.model.Domain.Evidence;

/**
 * Evidence identifiers for topic agents. A board holds each identifier once, but the merge step needs to
 * know every topic that found a document, and at which rank. Each topic therefore stores its own entry,
 * named document plus agent, in rank order, and the merge step folds them back into one document.
 */
public final class TopicEvidence {
    private static final String MARKER = "#topic:";

    private TopicEvidence() { }

    public static String id(String documentId, String agentId) {
        if (agentId.contains(MARKER)) {
            throw new IllegalArgumentException("An agent identifier may not contain " + MARKER);
        }
        return documentId + MARKER + agentId;
    }

    public static boolean isTopicEvidence(Evidence evidence) {
        return evidence.id().contains(MARKER);
    }

    /** The last marker is the real one, because document identifiers come from mail and may contain it. */
    public static String documentId(String evidenceId) {
        return evidenceId.substring(0, evidenceId.lastIndexOf(MARKER));
    }

    public static String agentId(String evidenceId) {
        return evidenceId.substring(evidenceId.lastIndexOf(MARKER) + MARKER.length());
    }
}
