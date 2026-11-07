package org.supermas.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.supermas.model.Domain.Failure;
import org.supermas.model.Domain.FailureKind;
import org.supermas.model.Domain.ModelConfig;
import org.supermas.model.Domain.ModelRequest;
import org.supermas.model.Domain.ModelResponse;

/**
 * Record and replay of model replies, so a run with a real model can be reproduced later with no model and no
 * network (on a weaker computer, or in CI). A reply is stored under a hash of the model name, the trusted prompt and
 * the untrusted data, so a replay answers only exactly the requests that were recorded; any change to a prompt, the
 * board's data or the model is a miss, reported as an explicit failure rather than a silent fallback.
 *
 * <p>Only successful replies are recorded. Files hold the model, the agent and the reply, not the prompt. Record only
 * synthetic or benchmark emails: a reply quotes its email.
 */
public final class RecordedLlm implements LocalLlm {
    public static final String SCHEMA = "llm-recording/v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Optional<LocalLlm> delegate;
    private final Path folder;

    private RecordedLlm(Optional<LocalLlm> delegate, Path folder) {
        this.delegate = delegate;
        this.folder = folder;
    }

    /** Passes every request to {@code delegate} and records each successful reply in {@code folder}. */
    public static RecordedLlm recording(LocalLlm delegate, Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the recording folder " + folder, e);
        }
        return new RecordedLlm(Optional.of(delegate), folder);
    }

    /** Answers only from the replies recorded in {@code folder}. */
    public static RecordedLlm replay(Path folder) {
        return new RecordedLlm(Optional.empty(), folder);
    }

    /** The file name a request is recorded under. */
    static String key(ModelRequest request, ModelConfig config) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : new String[] {config.model(), request.systemPrompt(), request.untrustedData()}) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest()) + ".json";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    @Override
    public ModelResponse complete(ModelRequest request, ModelConfig config) {
        int input = request.systemPrompt().length() + request.untrustedData().length();
        if (input > request.maxInputCharacters()) {
            return failure(FailureKind.BUDGET_EXHAUSTED, "The model input has " + input + " characters, over the budget of "
                    + request.maxInputCharacters());
        }
        Path file = folder.resolve(key(request, config));
        if (delegate.isEmpty()) {
            return replayed(file, config);
        }
        ModelResponse response = delegate.get().complete(request, config);
        if (response.output().isPresent() && response.failure().isEmpty()) {
            record(file, request, config, response.output().get());
        }
        return response;
    }

    private ModelResponse replayed(Path file, ModelConfig config) {
        if (!Files.isRegularFile(file)) {
            return failure(FailureKind.CONNECTION, "No recorded reply of " + config.model() + " for this request in "
                    + folder + " (the prompt, the data or the model changed since the recording)");
        }
        try {
            JsonNode recorded = JSON.readTree(file.toFile());
            JsonNode output = recorded.path("output");
            if (!SCHEMA.equals(recorded.path("schema").asText()) || !output.isTextual() || output.asText().isBlank()) {
                return failure(FailureKind.INVALID_OUTPUT, "The recorded reply " + file.getFileName() + " is not a "
                        + SCHEMA + " file");
            }
            if (output.asText().length() > config.maxOutputCharacters()) {
                return failure(FailureKind.INVALID_OUTPUT, "The recorded reply is over the limit of "
                        + config.maxOutputCharacters() + " characters");
            }
            return new ModelResponse(Optional.of(output.asText()), Optional.empty());
        } catch (IOException e) {
            return failure(FailureKind.INVALID_OUTPUT, "The recorded reply " + file.getFileName() + " cannot be read");
        }
    }

    private void record(Path file, ModelRequest request, ModelConfig config, String output) {
        ObjectNode entry = JSON.createObjectNode();
        entry.put("schema", SCHEMA);
        entry.put("model", config.model());
        entry.put("agentId", request.agentId());
        entry.put("output", output);
        try {
            Path temporary = Files.createTempFile(folder, "recording", ".tmp");
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), entry);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // A failed recording must not fail the reply; the next run simply misses it on replay.
            System.err.println("Could not record the model reply in " + file + ": " + e.getMessage());
        }
    }

    private static ModelResponse failure(FailureKind kind, String message) {
        return new ModelResponse(Optional.empty(), Optional.of(new Failure(kind, message, false)));
    }
}
