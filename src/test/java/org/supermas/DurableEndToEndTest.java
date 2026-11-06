package org.supermas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.blackboard.export.MarkdownProjector;
import org.supermas.blackboard.impl.DefaultBlackboardFactory;
import org.supermas.blackboard.persistence.sqlite.SqliteBlackboardStore;
import org.supermas.coordinator.CoordinationStrategy;
import org.supermas.coordinator.Coordinator;
import org.supermas.memory.ConversationRecorder;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Action;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardStatus;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.Task;
import org.supermas.model.Domain.TaskStatus;
import org.supermas.prototype.EvidencePromotion;
import org.supermas.prototype.InMemoryAgentRegistry;
import org.supermas.prototype.LeasedCoordinator;
import org.supermas.prototype.ScoreRoutingStrategy;

/**
 * The end-to-end scenarios of {@link EndToEndTest}, unchanged, on the durable SQLite blackboard of
 * codex/blackboard-storage, through {@link LeasedCoordinator}. Then what only a durable board can show.
 */
class DurableEndToEndTest extends EndToEndTest {
    @TempDir
    Path data;

    private SqliteBlackboardStore store;

    @Override
    Coordinator coordinator(InMemoryAgentRegistry registry, CoordinationStrategy strategy, Clock clock,
                            ConversationRecorder recorder, EvidencePromotion promotion) {
        store = new SqliteBlackboardStore(data.resolve("blackboard.db"), clock);
        return new LeasedCoordinator(MetadataAgent.rulesOnly(), new DefaultBlackboardFactory(store), registry,
                new ScoreRoutingStrategy(0.4, 0.1), strategy, promotion, Optional.of(recorder), Duration.ofMinutes(5));
    }

    private BlackboardSnapshot studentRequest() {
        return handle("durable-thread", "Extension for CS204",
                "I am a student. Could I have an extension for the CS204 assignment? My student number is 20231187.");
    }

    @Test
    void theBoardSurvivesARestartOfTheStore() {
        BlackboardSnapshot state = studentRequest();

        SqliteBlackboardStore reopened = new SqliteBlackboardStore(data.resolve("blackboard.db"));
        BlackboardSnapshot restored = new DefaultBlackboardFactory(reopened).open(state.id()).snapshot();

        assertEquals(state, restored);
        assertEquals(BoardStatus.AWAITING_REVIEW, restored.status());
        assertEquals(1, restored.drafts().size());
    }

    @Test
    void everyTaskRanUnderALeaseAndEndedWithOneExecutionRecord() {
        BlackboardSnapshot state = studentRequest();

        List<Task> tasks = state.tasks();
        assertTrue(tasks.stream().noneMatch(task -> task.status() == TaskStatus.RUNNING), tasks.toString());
        long ended = tasks.stream().filter(task -> task.status() == TaskStatus.SUCCEEDED
                || task.status() == TaskStatus.FAILED).count();
        assertEquals(ended, state.executions().size());
        // Routing assessments read through lease-free views (step 12.1): they are no longer tasks.
        assertEquals(0, tasks.stream().filter(task -> task.action() == Action.ROUTE).count());
        assertTrue(state.routing().isPresent(), "the assessments are still recorded");
        assertTrue(tasks.stream().filter(task -> task.status() != TaskStatus.PENDING).allMatch(task -> task.agentId().isPresent()));
    }

    @Test
    void contributionsArePrivateAndOnlyTheCoordinatorWidensThem() {
        BlackboardSnapshot state = studentRequest();

        List<Evidence> evidence = state.evidence();
        List<Evidence> copies = evidence.stream().filter(item -> item.id().endsWith(EvidencePromotion.SUFFIX)).toList();
        assertTrue(evidence.stream().filter(item -> !copies.contains(item))
                .allMatch(item -> item.permittedAgentIds().size() == 1), "every agent submission is private");
        assertTrue(!copies.isEmpty(), "context findings reached the reply agents");
        for (Evidence copy : copies) {
            String original = copy.id().substring(0, copy.id().length() - EvidencePromotion.SUFFIX.length());
            Evidence source = evidence.stream().filter(item -> item.id().equals(original)).findFirst().orElseThrow();
            assertEquals(source.provenance(), copy.provenance());
            assertEquals(source.text(), copy.text());
            assertTrue(!copy.permittedAgentIds().containsAll(source.permittedAgentIds()), "the author keeps its original");
        }
        // The staff guide never reaches a student-facing copy.
        assertTrue(copies.stream().noneMatch(item -> item.id().startsWith("kb:teaching-guide")
                && item.permittedAgentIds().contains("student")));
    }

    @Test
    void theMissingInformationInferenceReachesEveryRoleThatListsTheCheck() {
        // A meeting request with no date: the phrasing the context pipeline test uses to trigger the check.
        BlackboardSnapshot state = handle("vague-thread", "Meeting", "Could you meet about this?");

        Evidence original = state.evidence().stream().filter(item -> item.id().equals("missing-information"))
                .findFirst().orElseThrow(() -> new AssertionError(state.executions().toString()));
        Evidence copy = state.evidence().stream()
                .filter(item -> item.id().equals("missing-information" + EvidencePromotion.SUFFIX)).findFirst().orElseThrow();
        assertEquals(Set.of("missing-information"), original.permittedAgentIds());
        // Every bundled role lists the check as a source in its JSON file; no context agent does.
        assertEquals(Set.of("student", "teacher", "administration", "research", "generic"), copy.permittedAgentIds());
        assertEquals(original.text(), copy.text());
    }

    @Test
    void theBoardIsProjectedToMarkdownThatAuditsAsFaithful() throws Exception {
        BlackboardSnapshot state = studentRequest();

        try (MarkdownProjector projector = new MarkdownProjector(store, data.resolve("super-mas-vault"))) {
            projector.reconcile();
            assertTrue(projector.audit(state.id()).valid(), projector.audit(state.id()).detail());
            assertTrue(java.nio.file.Files.exists(projector.boardDirectory(state.id()).resolve("current.md")));
        }
    }
}
