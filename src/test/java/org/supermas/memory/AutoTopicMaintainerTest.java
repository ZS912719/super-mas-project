package org.supermas.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.supermas.context.ProjectIndexes;
import org.supermas.context.TopicCatalog;
import org.supermas.context.TopicDiscovery;
import org.supermas.context.TopicRegrouper;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Scope;

/** The user does nothing: recording mail is enough for topics to appear and for new mail to join them. */
class AutoTopicMaintainerTest {
    private static final Instant AT = Instant.parse("2026-11-03T10:00:00Z");
    private static final String[][] GROUPS = {
            {"exam", "grading", "rubric", "marks", "retake", "invigilation", "results", "transcript"},
            {"room", "booking", "projector", "auditorium", "seminar", "reservation", "building", "floor"},
            {"grant", "funding", "proposal", "budget", "reviewers", "laboratory", "equipment", "consortium"}};

    private final ProjectIndexes indexes = ProjectIndexes.inMemory();
    private final TopicCatalog catalog = TopicCatalog.inMemory();
    private final IndexConversationRepository repository = new IndexConversationRepository(indexes,
            project -> Optional.of(new ContextSource("mail", project, URI.create("file:///unused"), Set.of("reader"))));
    private final ConversationRecorder recorder = new ConversationRecorder(repository, MessageTagger.discovered(catalog));
    private final AutoTopicMaintainer maintainer = new AutoTopicMaintainer(
            new TopicRegrouper(indexes, catalog, new TopicDiscovery(TopicDiscovery.Settings.defaults())), 18);

    @AfterEach
    void close() throws Exception {
        indexes.close();
    }

    private void receive(String thread, String text, int minute) {
        Email email = new Email("<" + thread + "-" + minute + "@x>", new Scope("p", thread), "a@x", List.of(), "Hello",
                text, AT.plusSeconds(60L * minute), Map.of());
        recorder.onReceived(email, new Analysis(List.of(), List.of(), Map.of()));
    }

    private static String text(int g, int t) {
        StringBuilder text = new StringBuilder("Hello, thanks. ");
        for (int w = 0; w < 4; w++) {
            text.append(GROUPS[g][(t + w * 3) % GROUPS[g].length]).append(' ');
        }
        return text.append(GROUPS[g][t % 2]).toString();
    }

    @Test
    void topicsAppearOnceEnoughMailArrivedAndNewMailJoinsThem() {
        recorder.addListener(maintainer);
        int minute = 0;
        for (int g = 0; g < GROUPS.length; g++) {
            for (int t = 0; t < 6; t++) {
                assertTrue(catalog.topics("p").isEmpty() || minute >= 18);
                receive("g" + g + "-t" + t, text(g, t), minute++);
            }
        }
        assertEquals(3, catalog.topics("p").size());
        assertTrue(maintainer.lastRefresh("p").isPresent());

        receive("new-thread", "Is the auditorium projector booking confirmed?", minute);

        Set<String> tags = indexes.forProject("p").find("<new-thread-" + minute + "@x>").orElseThrow().topicTags();
        assertEquals(1, tags.size());
        assertTrue(tags.iterator().next().startsWith(TopicCatalog.TAG_PREFIX));
    }

    @Test
    void draftsDoNotTriggerRegrouping() {
        recorder.addListener(maintainer);
        for (int i = 0; i < 30; i++) {
            recorder.onDraft(new org.supermas.model.Domain.BoardId(new Scope("p", "t"), "task" + i),
                    new org.supermas.model.Domain.Draft(1, "s", "b", "agent", "e",
                            org.supermas.model.Domain.DraftOrigin.MODEL_GENERATED,
                            org.supermas.model.Domain.ReviewStatus.PROPOSED, AT));
        }
        assertFalse(maintainer.lastRefresh("p").isPresent());
    }
}
