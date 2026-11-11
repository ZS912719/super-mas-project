package org.supermas.prototype;

import org.supermas.llm.LlmSettings;
import org.supermas.llm.LocalLlm;
import org.supermas.llm.OpenAiCompatibleLlm;
import org.supermas.llm.RecordedLlm;
import org.supermas.model.Domain.ModelConfig;

/** The model engine and configuration the settings name, as the prototype builds them (test-scope benchmarks). */
final class BenchmarkEngines {
    private BenchmarkEngines() { }

    static LocalLlm engine(LlmSettings llm) {
        LocalLlm engine = switch (llm.provider()) {
            case LOCAL -> new OpenAiCompatibleLlm();
            case ONLINE -> OpenAiCompatibleLlm.online(llm.apiKey().orElseThrow());
            case REPLAY -> RecordedLlm.replay(llm.cache().orElseThrow());
            default -> throw new IllegalArgumentException("This benchmark needs a model, not " + llm.provider());
        };
        if (llm.provider() != LlmSettings.Provider.REPLAY && llm.cache().isPresent()) {
            engine = RecordedLlm.recording(engine, llm.cache().get());
        }
        return engine;
    }

    static ModelConfig config(LlmSettings llm) {
        return new ModelConfig(llm.model(), llm.endpoint(), llm.provider() == LlmSettings.Provider.ONLINE
                ? PrototypeApp.ONLINE_TIMEOUT : PrototypeApp.LLM_TIMEOUT, PrototypeApp.LLM_MAX_OUTPUT);
    }

    static String label(LlmSettings llm) {
        return switch (llm.provider()) {
            case ONLINE -> "Online model (development stand-in)";
            case REPLAY -> "Recorded model";
            default -> "Local model";
        };
    }
}
