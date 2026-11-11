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
        // en-01 asks for an extension; en-02 (the owner's own reply, here without an owner setting) and en-04 (a
        // broadcast) need no reply, and a
        // batch run stops their boards without a draft (plan reply-needed).
        assertEquals(List.of(BoardStatus.AWAITING_REVIEW, BoardStatus.STOPPED, BoardStatus.STOPPED),
                outcomes.stream().map(outcome -> outcome.state().status()).toList(), first.toString(StandardCharsets.UTF_8));
        assertTrue(outcomes.get(1).state().drafts().isEmpty() && outcomes.get(2).state().drafts().isEmpty());
        assertEquals(1, outcomes.get(0).state().drafts().size());
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
