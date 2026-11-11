package org.supermas.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.metadata.ReplyNeedClassifier.Verdict;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Scope;

/** Each rule of the reply-need classifier, with its reason (plan reply-needed). */
class ReplyNeedClassifierTest {
    private static final ReplyNeedClassifier RULES = new ReplyNeedClassifier(Set.of("E.Martin@example.edu"));

    private static Email mail(String from, List<String> to, Map<String, String> headers) {
        return new Email("<m@x>", new Scope("uni", "<m@x>"), from, to, "Subject", "unused", Instant.EPOCH, headers);
    }

    private static ReplyNeedClassifier.Decision decide(String from, List<String> to, String text, List<String> acts) {
        return RULES.classify(mail(from, to, Map.of()), text, acts);
    }

    @Test
    void questionsRequestsAndProposalsNeedAReply() {
        assertEquals(Verdict.NEEDED, decide("s@students.example.edu", List.of("e.martin@example.edu"),
                "Dear Prof. Martin,\n\nCould I have an extension?", List.of()).verdict());
        assertEquals(List.of("contains a request"), decide("s@x.org", List.of("me@x.org"), "Send me the file by Friday.",
                List.of("request")).reasons());
        assertEquals(Verdict.NEEDED, decide("s@x.org", List.of("me@x.org"), "We could meet on Thursday.", List.of("propose")).verdict());
    }

    @Test
    void theOwnersOwnMailNeedsNoReply() {
        ReplyNeedClassifier.Decision own = decide("e.martin@example.edu", List.of("s@x.org"), "Could you send it?", List.of());
        assertEquals(Verdict.NOT_NEEDED, own.verdict());
        assertEquals(List.of("sent by the mailbox owner"), own.reasons());
    }

    @Test
    void automatedAndBulkMailNeedsNoReply() {
        assertEquals(Verdict.NOT_NEEDED, decide("noreply@service.example", List.of("me@x.org"), "Your code is 1234. Questions?", List.of()).verdict());
        assertEquals(Verdict.NOT_NEEDED, RULES.classify(mail("news@x.org", List.of("me@x.org"), Map.of("List-Id", "<l.x.org>")),
                "Read our news", List.of()).verdict());
        assertEquals(Verdict.NOT_NEEDED, RULES.classify(mail("a@x.org", List.of("me@x.org"), Map.of("Precedence", "bulk")),
                "Offer", List.of()).verdict());
        assertEquals(Verdict.NOT_NEEDED, decide("web@x.org", List.of("me@x.org"),
                "This in an automated e-mail sent out from the web site.", List.of()).verdict());
        assertEquals(Verdict.NEEDED, RULES.classify(mail("a@x.org", List.of("me@x.org"), Map.of("Auto-Submitted", "no")),
                "Can you help?", List.of()).verdict(), "Auto-Submitted: no is a person");
    }

    @Test
    void broadcastsNeedNoReplyUnlessTheyAsk() {
        assertEquals(Verdict.NOT_NEEDED, decide("registrar@x.edu", List.of("all-students@x.edu"), "Enrolment closes on 20 November.",
                List.of()).verdict());
        List<String> many = java.util.stream.IntStream.range(0, 12).mapToObj(i -> "p" + i + "@x.org").toList();
        assertEquals(List.of("broadcast: sent to 12 recipients"), decide("a@x.org", many, "The meeting moved to B204.", List.of()).reasons());
        assertEquals(Verdict.UNSURE, decide("a@x.org", many, "Who can bring a projector?", List.of()).verdict());
        assertTrue(decide("a@x.org", many, "Who can bring a projector?", List.of()).replyNeeded(), "unsure asks for a reply");
    }

    @Test
    void thanksAndInformationOnlyNeedNoReply() {
        assertEquals(List.of("only thanks or an acknowledgement"), decide("s@x.org", List.of("me@x.org"),
                "Thank you very much, Prof. Martin. That really helps.\n\nSarah", List.of()).reasons());
        ReplyNeedClassifier.Decision info = decide("s@x.org", List.of("me@x.org"),
                "The lecture on Monday 9 November is cancelled. The slides are online at https://x.org/s?id=4.", List.of());
        assertEquals(Verdict.NOT_NEEDED, info.verdict(), "a question mark inside a link is not a question");
        assertFalse(info.replyNeeded());
    }

    @Test
    void theMetadataAgentRecordsTheVerdictAndATag() {
        Email email = new Email("<t@x>", new Scope("uni", "<t@x>"), "s@x.org", List.of("me@x.org"), "Thanks",
                "Thanks a lot!\n\nSarah", Instant.EPOCH, Map.of());
        var analysis = MetadataAgent.rulesOnly().analyze(email);
        assertEquals("NOT_NEEDED", analysis.metadata().get(MetadataAgent.KEY_REPLY_NEED));
        assertTrue(analysis.metadata().get(MetadataAgent.KEY_REPLY_NEED_REASONS).startsWith("["));
        assertTrue(analysis.initialTags().contains("reply:not-needed"));
    }
}
