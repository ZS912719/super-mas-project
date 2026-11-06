package org.supermas.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.supermas.context.TopicDiscovery.Cluster;
import org.supermas.context.TopicDiscovery.ThreadText;

class TopicDiscoveryTest {
    private static final String[][] GROUPS = {
            {"exam", "grading", "rubric", "marks", "retake", "invigilation", "results", "transcript"},
            {"room", "booking", "projector", "auditorium", "seminar", "reservation", "building", "floor"},
            {"grant", "funding", "proposal", "budget", "reviewers", "laboratory", "equipment", "consortium"}};

    /** Six threads per group, each using a different mix of its group's words and some shared filler. */
    static List<ThreadText> threads() {
        List<ThreadText> threads = new ArrayList<>();
        for (int g = 0; g < GROUPS.length; g++) {
            for (int t = 0; t < 6; t++) {
                StringBuilder text = new StringBuilder("Hello, thanks for your message. Regards. ");
                for (int w = 0; w < 4; w++) {
                    text.append(GROUPS[g][(t + w * 3) % GROUPS[g].length]).append(' ');
                }
                text.append(GROUPS[g][t % 2]).append(' ');
                threads.add(new ThreadText("g" + g + "-t" + t, text.toString()));
            }
        }
        return threads;
    }

    private static String groupOf(String threadId) {
        return threadId.substring(0, 2);
    }

    @Test
    void separableGroupsBecomeOneTopicEachWithTheirOwnWords() {
        List<Cluster> clusters = new TopicDiscovery(TopicDiscovery.Settings.defaults()).discover(threads());

        assertEquals(3, clusters.size());
        for (Cluster cluster : clusters) {
            assertEquals(1, cluster.threadIds().stream().map(TopicDiscoveryTest::groupOf).distinct().count(),
                    "one group per topic: " + cluster);
            int group = Integer.parseInt(groupOf(cluster.threadIds().iterator().next()).substring(1));
            assertTrue(Set.of(GROUPS[group]).containsAll(cluster.keywords()), "keywords from its group: " + cluster);
            assertFalse(cluster.keywords().contains("thanks"));
        }
    }

    @Test
    void theSameInputGivesTheSameTopics() {
        TopicDiscovery discovery = new TopicDiscovery(TopicDiscovery.Settings.defaults());
        List<ThreadText> shuffled = new ArrayList<>(threads());
        java.util.Collections.reverse(shuffled);

        assertEquals(discovery.discover(threads()), discovery.discover(shuffled));
    }

    @Test
    void aMailboxTooSmallForTopicsHasNone() {
        assertTrue(new TopicDiscovery(TopicDiscovery.Settings.defaults()).discover(threads().subList(0, 5)).isEmpty());
    }

    @Test
    void oneThreadAloneCannotPutAWordIntoATopicsKeywords() {
        List<ThreadText> threads = new ArrayList<>(threads());
        threads.set(0, new ThreadText("g0-t0", threads.get(0).text()
                + " clickhere clickhere clickhere clickhere clickhere clickhere"));

        List<Cluster> clusters = new TopicDiscovery(TopicDiscovery.Settings.defaults()).discover(threads);

        assertTrue(clusters.stream().noneMatch(cluster -> cluster.keywords().contains("clickhere")));
    }
}
