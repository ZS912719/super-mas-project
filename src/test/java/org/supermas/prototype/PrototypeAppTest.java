package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.model.Domain.BoardStatus;

/** The runnable prototype on a few synthetic emails, twice on the same data folder. */
class PrototypeAppTest {
    @TempDir
    Path temp;

    private List<PrototypeApp.Outcome> run(Path emails, ByteArrayOutputStream out) throws Exception {
        return PrototypeApp.run(PrototypeApp.Options.parse(new String[] {emails.toString(), "--data",
                temp.resolve("data").toString()}), new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    @Test
    void emailsReachADraftAwaitingReviewAndTheStateIsKeptForTheNextRun() throws Exception {
        Path emails = Files.createDirectories(temp.resolve("emails"));
        for (String name : List.of("en-01.eml", "en-02.eml", "en-04.eml")) {
            Files.copy(Path.of("benchmark/data/synthetic-dev", name), emails.resolve(name));
        }
        Files.writeString(emails.resolve("notes.txt"), "not an email");

        ByteArrayOutputStream first = new ByteArrayOutputStream();
        List<PrototypeApp.Outcome> outcomes = run(emails, first);

        assertEquals(3, outcomes.size(), "only .eml files are read");
        // Note-only (since B70): en-02 and en-04 need no reply and are noted, but every email still gets a draft.
        assertTrue(outcomes.stream().allMatch(outcome -> outcome.state().status() == BoardStatus.AWAITING_REVIEW),
                first.toString(StandardCharsets.UTF_8));
        assertTrue(outcomes.stream().allMatch(outcome -> outcome.state().drafts().size() == 1));
        assertEquals(List.of("NEEDED", "NOT_NEEDED", "NOT_NEEDED"), outcomes.stream()
                .map(outcome -> outcome.state().analysis().metadata().get(org.supermas.metadata.MetadataAgent.KEY_REPLY_NEED)).toList());
        assertEquals(2, first.toString(StandardCharsets.UTF_8).split("Note: probably no reply needed", -1).length - 1);
        assertTrue(Files.exists(temp.resolve("data/blackboard.db")));
        assertTrue(Files.isDirectory(temp.resolve("data/super-mas-vault/generated")));

        // The second run reads the same database and finds the first run's emails as stored history.
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        List<PrototypeApp.Outcome> again = run(emails.resolve("en-01.eml"), second);
        assertEquals(BoardStatus.AWAITING_REVIEW, again.get(0).state().status());
        assertTrue(again.get(0).state().evidence().size() > outcomes.get(0).state().evidence().size(),
                second.toString(StandardCharsets.UTF_8));
    }

    @Test
    void theStopForEmailsThatNeedNoReplyIsOffByDefaultAndWorksWhenSwitchedOn() throws Exception {
        org.supermas.model.Domain.Email broadcast = new org.supermas.metadata.MimeEmailParser()
                .parse(Files.readAllBytes(Path.of("benchmark/data/synthetic-dev/en-04.eml")), "uni");
        try (org.supermas.context.ProjectIndexes indexes = org.supermas.context.ProjectIndexes.inMemory()) {
            PrototypeApp.Wiring stopping = PrototypeApp.Wiring.create(indexes, new PrototypeApp.Options(null,
                    Path.of("src/test/resources/university/knowledge"), temp.resolve("on"), "uni"),
                    org.supermas.agent.role.RoleCatalog.bundled(), true);
            var state = stopping.coordinator.run(stopping.coordinator.receive(broadcast), PrototypeApp.BLACKBOARD_LIMITS);
            assertEquals(BoardStatus.STOPPED, state.status());
            assertTrue(state.drafts().isEmpty());
        }
    }

    @Test
    void badArgumentsAreRefusedWithTheUsage() {
        assertThrows(IllegalArgumentException.class, () -> PrototypeApp.Options.parse(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> PrototypeApp.Options.parse(new String[] {"x", "--data"}));
        assertThrows(IllegalArgumentException.class, () -> PrototypeApp.Options.parse(new String[] {"x", "--send", "y"}));
    }

    @Test
    void theModelComesFromTheEnvironmentOrTheFlagsAndTheTemplateIsTheDefault() {
        assertEquals(org.supermas.llm.LlmSettings.Provider.TEMPLATE, PrototypeApp.Options.parse(new String[] {"x"}).llm().provider());
        java.util.Map<String, String> env = java.util.Map.of("SUPERMAS_LLM_PROVIDER", "local", "SUPERMAS_LLM_MODEL", "llama3.2:3b");
        assertEquals("llama3.2:3b", PrototypeApp.Options.parse(new String[] {"x"}, env).llm().model());
        assertEquals(org.supermas.llm.LlmSettings.Provider.TEMPLATE,
                PrototypeApp.Options.parse(new String[] {"x"}).llm().provider(), "tests ignore the environment");
        assertEquals("other", PrototypeApp.Options.parse(new String[] {"x", "--llm-model", "other"}, env).llm().model());
        assertThrows(IllegalArgumentException.class, () -> PrototypeApp.Options.parse(new String[] {"x", "--llm-endpoint",
                "http://127.0.0.1:11434"}), "an endpoint needs a model");
    }
}
