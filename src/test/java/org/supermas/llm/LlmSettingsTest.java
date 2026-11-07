package org.supermas.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.supermas.llm.LlmSettings.Provider;

/** Choosing the engine by configuration, and the guardrails of the online stand-in. */
class LlmSettingsTest {
    private static final String KEY = "sk-or-v1-secret-key-for-tests";

    @Test
    void nothingConfiguredMeansTheTemplate() {
        LlmSettings settings = LlmSettings.from(Map.of("PATH", "/usr/bin"), Map.of());
        assertEquals(Provider.TEMPLATE, settings.provider());
        assertFalse(settings.leavesComputer());
    }

    @Test
    void anEndpointAloneMeansALocalEngineAsBefore() {
        LlmSettings settings = LlmSettings.from(Map.of(),
                Map.of("--llm-endpoint", "http://127.0.0.1:11434", "--llm-model", "qwen2.5:1.5b"));
        assertEquals(Provider.LOCAL, settings.provider());
        assertEquals("qwen2.5:1.5b", settings.model());
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(),
                Map.of("--llm-endpoint", "http://127.0.0.1:11434")), "a model is required");
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(),
                Map.of("--llm-provider", "local", "--llm-endpoint", "https://openrouter.ai/api/v1", "--llm-model", "m")),
                "local stays loopback only");
    }

    @Test
    void movingToAnotherComputerIsTwoEnvironmentVariables() {
        LlmSettings settings = LlmSettings.from(Map.of(LlmSettings.PROVIDER, "local", LlmSettings.MODEL, "llama3.2:3b"),
                Map.of());
        assertEquals(Provider.LOCAL, settings.provider());
        assertEquals(LlmSettings.DEFAULT_LOCAL, settings.endpoint());
    }

    @Test
    void flagsOverrideTheEnvironment() {
        LlmSettings settings = LlmSettings.from(Map.of(LlmSettings.PROVIDER, "online", LlmSettings.MODEL, "online-model",
                LlmSettings.API_KEY, KEY), Map.of("--llm-provider", "local", "--llm-model", "local-model"));
        assertEquals(Provider.LOCAL, settings.provider());
        assertEquals("local-model", settings.model());
    }

    @Test
    void theOnlineProviderNeedsAKeyFromTheEnvironmentAndHttps() {
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(),
                Map.of("--llm-provider", "online", "--llm-model", "m")), "no key");
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(LlmSettings.API_KEY, KEY),
                Map.of("--llm-provider", "online", "--llm-model", "m", "--llm-endpoint", "http://openrouter.ai/api/v1")),
                "plain HTTP would expose the key");
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(),
                Map.of("--llm-api-key", KEY)), "there is no flag for the key");

        LlmSettings online = LlmSettings.from(Map.of(LlmSettings.API_KEY, KEY),
                Map.of("--llm-provider", "online", "--llm-model", "meta-llama/llama-3.2-3b-instruct"));
        assertEquals(LlmSettings.DEFAULT_ONLINE, online.endpoint());
        assertTrue(online.leavesComputer());
        assertFalse(online.onlineForMail(), "the mailbox needs its own opt-in");
        assertTrue(online.describe().contains("openrouter.ai"), online.describe());
        assertFalse(online.describe().contains(KEY));
        assertFalse(online.toString().contains(KEY), "the key never reaches a log");
        assertTrue(LlmSettings.from(Map.of(LlmSettings.API_KEY, KEY, LlmSettings.ONLINE_MAIL, "yes"),
                Map.of("--llm-provider", "online", "--llm-model", "m")).onlineForMail());
    }

    @Test
    void replayNeedsRecordingsAndTheTemplateHasNone(@TempDir Path folder) {
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(),
                Map.of("--llm-provider", "replay", "--llm-model", "m")));
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(),
                Map.of("--llm-provider", "replay", "--llm-model", "m", "--llm-cache", folder.resolve("missing").toString())));
        assertEquals(Provider.REPLAY, LlmSettings.from(Map.of(),
                Map.of("--llm-provider", "replay", "--llm-model", "m", "--llm-cache", folder.toString())).provider());
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(),
                Map.of("--llm-cache", folder.toString())));
        assertThrows(IllegalArgumentException.class, () -> LlmSettings.from(Map.of(LlmSettings.PROVIDER, "cloud"), Map.of()));
    }

    @Test
    void theOnlineEndpointIsTheChatPathUnderTheApiBase() {
        assertEquals(URI.create("https://openrouter.ai/api/v1/chat/completions"),
                OpenAiCompatibleLlm.onlineChatEndpoint(URI.create("https://openrouter.ai/api/v1/")));
        for (String base : new String[] {"http://openrouter.ai/api/v1", "https://user:pw@openrouter.ai/api/v1",
                "https://openrouter.ai/api/v1?key=x", "file:///etc/passwd"}) {
            assertThrows(IllegalArgumentException.class, () -> OpenAiCompatibleLlm.onlineChatEndpoint(URI.create(base)), base);
        }
    }
}
