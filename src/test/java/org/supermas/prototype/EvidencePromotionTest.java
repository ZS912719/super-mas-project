package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.supermas.prototype.Fixtures.agent;
import static org.supermas.prototype.Fixtures.evidence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.context.TopicEvidence;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Scope;

class EvidencePromotionTest {
    private static final Scope SCOPE = new Scope("project", "thread");
    private static final AgentConfig AUTHOR = agent("topic", Set.of(), Set.of("mail", "topic"), false);
    private static final AgentConfig STUDENT = agent("student", Set.of(), Set.of("mail", "topic"), false);
    private static final AgentConfig TEACHER = agent("teacher", Set.of(), Set.of("mail"), false);
    private static final AgentConfig OUTSIDER = agent("outsider", Set.of(), Set.of("wiki"), false);
    private static final List<AgentConfig> AGENTS = List.of(AUTHOR, STUDENT, TEACHER, OUTSIDER);

    private static Evidence retrieved(String document) {
        return evidence(TopicEvidence.id(document, "topic"), SCOPE, EvidenceKind.RETRIEVED_EVIDENCE, "mail", Set.of("topic"));
    }

    @Test
    void retrievedEvidenceReachesOnlyAgentsListingTheSourceAndAllowedByTheDocument() {
        EvidencePromotion promotion = new EvidencePromotion((project, document, source) ->
                document.equals("doc") && source.equals("mail") ? Optional.of(Set.of("topic", "student")) : Optional.empty());

        List<Evidence> copies = promotion.promote(AUTHOR, List.of(retrieved("doc")), AGENTS);

        // The teacher lists the source, but the document's own readers leave it out.
        assertEquals(1, copies.size());
        assertEquals(Set.of("student"), copies.get(0).permittedAgentIds());
        assertEquals("doc", TopicEvidence.documentId(copies.get(0).id()), "the merge still sees one document");
    }

    @Test
    void aDocumentTheIndexDoesNotKnowIsNotPromoted() {
        assertTrue(EvidencePromotion.inferencesOnly().promote(AUTHOR, List.of(retrieved("doc")), AGENTS).isEmpty());
    }

    @Test
    void anInferenceReachesAgentsListingItsAuthorAndOnlyIfTheAuthorIsItsSource() {
        Evidence own = evidence("note", SCOPE, EvidenceKind.MODEL_INFERENCE, "topic", Set.of("topic"));
        Evidence borrowed = evidence("other", SCOPE, EvidenceKind.MODEL_INFERENCE, "mail", Set.of("topic"));

        List<Evidence> copies = EvidencePromotion.inferencesOnly().promote(AUTHOR, List.of(own, borrowed), AGENTS);

        assertEquals(1, copies.size());
        assertEquals("note" + EvidencePromotion.SUFFIX, copies.get(0).id());
        assertEquals(Set.of("student"), copies.get(0).permittedAgentIds());
    }

    @Test
    void sharedOrCopiedItemsAreNeverPromotedAgain() {
        Evidence shared = evidence("note", SCOPE, EvidenceKind.MODEL_INFERENCE, "topic", Set.of("topic", "student"));
        Evidence copy = evidence("x" + EvidencePromotion.SUFFIX, SCOPE, EvidenceKind.MODEL_INFERENCE, "topic", Set.of("topic"));

        assertTrue(EvidencePromotion.inferencesOnly().promote(AUTHOR, List.of(shared, copy), AGENTS).isEmpty());
    }
}
