package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.model.Domain.Scope;

class TopicCandidateFilterTest {
    private static final Scope HERE = TopicTestSupport.HERE;
    private final TopicTestSupport support = new TopicTestSupport(Set.of("a", "b", "c", "d"));

    private TopicContextAgent subject(String id, String... keywords) {
        return new TopicContextAgent(support.subject(id, id, List.of(keywords)), support.retriever);
    }

    private TopicContextAgent discussion() {
        return new TopicContextAgent(new TopicDefinition(TopicType.DISCUSSION, "d", TopicTestSupport.PROJECT, "",
                List.of(), Set.of(TopicTestSupport.MAIL), 100), support.retriever);
    }

    private List<String> select(int max, List<TopicContextAgent> agents, String text) {
        return new TopicCandidateFilter(max).select(agents, TopicTestSupport.board(HERE, "s", text)).stream()
                .map(agent -> agent.config().id()).toList();
    }

    @AfterEach
    void close() throws Exception {
        support.close();
    }

    @Test
    void subjectsWithNoMatchDoNotRun() {
        assertEquals(List.of("a"), select(5, List.of(subject("a", "budget"), subject("b", "travel")), "the budget"));
    }

    @Test
    void onlyTheBestSubjectsRunEvenWithManyMatches() {
        List<TopicContextAgent> agents = List.of(subject("a", "budget", "absent", "missing"),
                subject("b", "budget", "travel", "absent"), subject("c", "budget", "travel", "visa"));
        assertEquals(List.of("c", "b"), select(2, agents, "budget travel visa"));
    }

    @Test
    void equalScoresResolveByIdentifierSoTheOrderIsStable() {
        assertEquals(List.of("a", "b"), select(2, List.of(subject("b", "budget"), subject("a", "budget"),
                subject("c", "budget")), "budget"));
    }

    @Test
    void theDiscussionAgentRunsFirstAndDoesNotUseASubjectPlace() {
        assertEquals(List.of("d", "a"), select(1, List.of(subject("a", "budget"), discussion()), "budget"));
    }

    @Test
    void aColdStartStillSelectsTheThread() {
        assertEquals(List.of("d"), select(3, List.of(subject("a", "budget"), discussion()), "nothing"));
    }

    @Test
    void anotherProjectsAgentsAreNeverSelected() {
        TopicContextAgent elsewhere = new TopicContextAgent(new TopicDefinition(TopicType.DISCUSSION, "a", "project-b",
                "", List.of(), Set.of(), 100), support.retriever);
        assertEquals(List.of(), select(3, List.of(elsewhere), "budget"));
    }
}
