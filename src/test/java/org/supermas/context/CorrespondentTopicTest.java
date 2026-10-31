package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;
import org.supermas.prototype.InMemoryBlackboard;

class CorrespondentTopicTest {
    private static final String AGENT = "correspondent";
    private static final String ALICE = "alice@example.org";
    private static final Scope HERE = TopicTestSupport.HERE;
    private static final Scope OTHER_THREAD = new Scope(TopicTestSupport.PROJECT, "thread-2");
    private static final SenderVerifier EVERYONE = email -> true;

    private final TopicTestSupport support = new TopicTestSupport(Set.of(AGENT));

    private TopicContextAgent agent(CorrespondentSettings settings) {
        return new TopicContextAgent(new TopicDefinition(TopicType.CORRESPONDENT, AGENT, TopicTestSupport.PROJECT, "",
                List.of(), Set.of(TopicTestSupport.MAIL), 10_000), support.retriever, settings);
    }

    private static CorrespondentSettings on(SenderVerifier verifier) {
        return new CorrespondentSettings(true, verifier);
    }

    private void history(String id, Scope scope, String sender, SenderVerifier verifiedAtIndexing) {
        Email earlier = new Email(id, scope, sender, List.of(), "", "", TopicTestSupport.AT, Map.of());
        Set<String> tags = TopicTagger.correspondentTag(earlier, verifiedAtIndexing).map(Set::of).orElse(Set.of());
        support.document(id, scope, TopicTestSupport.MAIL, Optional.of(MessageKind.RECEIVED), "budget figures",
                Set.of(AGENT), tags);
    }

    /** The test board's email always claims to come from Alice. */
    private List<String> found(TopicContextAgent agent, Scope scope) {
        InMemoryBlackboard board = TopicTestSupport.board(scope, "budget", "budget");
        agent.execute(null, board.accessFor(agent.config()));
        return board.snapshot().evidence().stream().map(Evidence::id).map(TopicEvidence::documentId).toList();
    }

    @AfterEach
    void close() throws Exception {
        support.close();
    }

    @Test
    void aVerifiedSendersOtherThreadsAreFound() {
        history("earlier", OTHER_THREAD, ALICE, EVERYONE);
        assertEquals(List.of("earlier"), found(agent(on(EVERYONE)), HERE));
    }

    @Test
    void itIsOffByDefault() {
        history("earlier", OTHER_THREAD, ALICE, EVERYONE);
        TopicContextAgent off = new TopicContextAgent(new TopicDefinition(TopicType.CORRESPONDENT, AGENT,
                TopicTestSupport.PROJECT, "", List.of(), Set.of(TopicTestSupport.MAIL), 10_000), support.retriever);
        assertTrue(found(off, HERE).isEmpty());
        assertEquals(0, off.assess(null, TopicTestSupport.board(HERE, "budget", "budget")).score());
    }

    @Test
    void theSwitchAloneTurnsItOffEvenWithAVerifiedSender() {
        history("earlier", OTHER_THREAD, ALICE, EVERYONE);
        TopicContextAgent off = agent(new CorrespondentSettings(false, EVERYONE));
        assertTrue(found(off, HERE).isEmpty());
        assertEquals(0, off.assess(null, TopicTestSupport.board(HERE, "budget", "budget")).score());
    }

    @Test
    void anEnabledSwitchWithTheDefaultVerifierStillFindsNothing() {
        history("earlier", OTHER_THREAD, ALICE, EVERYONE);
        assertTrue(found(agent(on(SenderVerifier.none())), HERE).isEmpty());
    }

    @Test
    void aForgedSenderGetsNoHistoryAndNoScore() {
        history("victim-history", OTHER_THREAD, ALICE, EVERYONE);
        SenderVerifier rejectsThisEmail = email -> !email.id().equals("incoming");
        TopicContextAgent agent = agent(on(rejectsThisEmail));
        assertTrue(found(agent, HERE).isEmpty());
        assertEquals(0, agent.assess(null, TopicTestSupport.board(HERE, "budget", "budget")).score());
    }

    @Test
    void forgedMailNeverJoinsAVictimsHistory() {
        history("forged-earlier", OTHER_THREAD, ALICE, SenderVerifier.none());
        assertTrue(found(agent(on(EVERYONE)), HERE).isEmpty());
    }

    @Test
    void anotherSendersHistoryIsNotReturned() {
        history("from-bob", OTHER_THREAD, "bob@example.org", EVERYONE);
        assertTrue(found(agent(on(EVERYONE)), HERE).isEmpty());
    }

    @Test
    void theSameSenderInAnotherProjectIsNotReturned() {
        support.sources.put("b-mail", new ContextSource("b-mail", "project-b", URI.create("file:///unused"), Set.of(AGENT)));
        Scope elsewhere = new Scope("project-b", "thread-2");
        Email earlier = new Email("other-project", elsewhere, ALICE, List.of(), "", "", TopicTestSupport.AT, Map.of());
        support.document("other-project", elsewhere, "b-mail", Optional.of(MessageKind.RECEIVED), "budget figures",
                Set.of(AGENT), Set.of(TopicTagger.correspondentTag(earlier, EVERYONE).orElseThrow()));
        assertTrue(found(agent(on(EVERYONE)), HERE).isEmpty());
        // On a board of project-b it is also nothing, because this agent is configured for project-a.
        assertTrue(found(agent(on(EVERYONE)), elsewhere).isEmpty());
    }

    @Test
    void aMessageTheAgentMayNotReadStaysHidden() {
        Email earlier = new Email("hidden", OTHER_THREAD, ALICE, List.of(), "", "", TopicTestSupport.AT, Map.of());
        support.document("hidden", OTHER_THREAD, TopicTestSupport.MAIL, Optional.of(MessageKind.RECEIVED), "budget",
                Set.of("someone-else"), Set.of(TopicTagger.correspondentTag(earlier, EVERYONE).orElseThrow()));
        assertTrue(found(agent(on(EVERYONE)), HERE).isEmpty());
    }

    @Test
    void aFailingVerifierMeansNotVerified() {
        history("earlier", OTHER_THREAD, ALICE, EVERYONE);
        SenderVerifier broken = email -> {
            throw new IllegalStateException("down");
        };
        assertTrue(found(agent(on(broken)), HERE).isEmpty());
    }

    @Test
    void aBlankSenderIsNeverVerified() {
        Email blank = new Email("x", HERE, " ", List.of(), "", "", TopicTestSupport.AT, Map.of());
        assertTrue(TopicTagger.correspondentTag(blank, EVERYONE).isEmpty());
    }

    @Test
    void theTagIgnoresCaseAndHidesTheAddress() {
        String tag = TopicTagger.senderTag("Alice@Example.org ");
        assertEquals(TopicTagger.senderTag(ALICE), tag);
        assertFalse(tag.contains("alice"));
    }

    @Test
    void candidateFilteringLeavesItOutWhenOff() {
        TopicContextAgent off = agent(CorrespondentSettings.off());
        assertTrue(new TopicCandidateFilter(3).select(List.of(off), TopicTestSupport.board(HERE, "s", "t")).isEmpty());
        assertEquals(1, new TopicCandidateFilter(3).select(List.of(agent(on(EVERYONE))),
                TopicTestSupport.board(HERE, "s", "t")).size());
    }
}
