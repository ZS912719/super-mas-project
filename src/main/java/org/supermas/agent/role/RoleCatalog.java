package org.supermas.agent.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.supermas.model.Domain.AgentConfig;
import org.supermas.persistence.AgentConfigRepository;

/**
 * The specialized agents' configuration: one JSON file per role and one prompt file per role, plus a prompt
 * shared by all ({@code common.txt}). The bundled catalogue lives on the class path ({@code agents/} and
 * {@code prompts/}); a folder with the same layout replaces it, so roles change without rebuilding:
 *
 * <pre>
 * roles/
 *   student.json  teacher.json  …
 *   prompts/common.txt  prompts/student.txt  …
 * </pre>
 *
 * <p>Each role's system prompt is the common prompt, the role prompt and the role's rules, in that order. Its
 * version is a short hash of that text, so any edit gives a new version with nothing to bump by hand.
 */
public final class RoleCatalog implements AgentConfigRepository {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}\\.txt");
    private static final String COMMON_PROMPT = "common.txt";
    private static final String COMPOSER_PROMPT = "composer.txt";

    private final Map<String, RoleDefinition> roles = new LinkedHashMap<>();
    private final Map<String, String> prompts = new LinkedHashMap<>();
    private final Optional<Path> folder;
    private final String commonPrompt;
    private final String composerPrompt;
    private final Function<String, Optional<String>> promptFiles;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private RoleCatalog(List<RoleDefinition> definitions, Function<String, Optional<String>> promptFiles,
                        Optional<Path> folder) {
        this.folder = folder;
        this.promptFiles = promptFiles;
        String common = promptFiles.apply(COMMON_PROMPT)
                .orElseThrow(() -> new IllegalArgumentException("The prompt folder needs " + COMMON_PROMPT));
        this.commonPrompt = common;
        // A folder catalogue without its own composer prompt uses the bundled one.
        this.composerPrompt = promptFiles.apply(COMPOSER_PROMPT).or(() -> resource("prompts/" + COMPOSER_PROMPT))
                .orElseThrow(() -> new IllegalStateException("The bundled " + COMPOSER_PROMPT + " is missing"));
        int fallbacks = 0;
        for (RoleDefinition role : definitions) {
            validate(role);
            if (roles.containsKey(role.id())) {
                throw new IllegalArgumentException("Role " + role.id() + " is defined twice");
            }
            fallbacks += role.fallback() ? 1 : 0;
            String rolePrompt = promptFiles.apply(role.prompt()).filter(text -> !text.isBlank())
                    .orElseThrow(() -> new IllegalArgumentException("Role " + role.id() + " has no prompt " + role.prompt()));
            roles.put(role.id(), role);
            prompts.put(role.id(), compose(common, rolePrompt, role.rules()));
        }
        if (fallbacks > 1) {
            throw new IllegalArgumentException("At most one role may be the fallback");
        }
    }

    /** The roles shipped with the code. */
    public static RoleCatalog bundled() {
        ObjectMapper reader = new ObjectMapper();
        List<RoleDefinition> definitions = new ArrayList<>();
        for (String name : resource("agents/index.txt").orElseThrow().lines().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList()) {
            try {
                definitions.add(reader.readValue(resource("agents/" + name).orElseThrow(), RoleDefinition.class));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read bundled role " + name, e);
            }
        }
        return new RoleCatalog(definitions, name -> FILE_NAME.matcher(name).matches()
                ? resource("prompts/" + name) : Optional.empty(), Optional.empty());
    }

    /** Every {@code *.json} in the folder is a role; prompts are in its {@code prompts} subfolder. */
    public static RoleCatalog fromFolder(Path folder) {
        ObjectMapper reader = new ObjectMapper();
        List<RoleDefinition> definitions = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
                definitions.add(reader.readValue(file.toFile(), RoleDefinition.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the roles in " + folder, e);
        }
        Path promptFolder = folder.resolve("prompts");
        return new RoleCatalog(definitions, name -> {
            if (!FILE_NAME.matcher(name).matches()) {
                return Optional.empty();
            }
            try {
                Path file = promptFolder.resolve(name);
                return Files.isRegularFile(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
                        : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Optional.of(folder));
    }

    public List<RoleDefinition> roles() {
        return List.copyOf(roles.values());
    }

    public Optional<RoleDefinition> role(String id) {
        return Optional.ofNullable(roles.get(id));
    }

    /** The full trusted system prompt of a role. */
    public String systemPrompt(String id) {
        return Optional.ofNullable(prompts.get(id)).orElseThrow(() -> new IllegalArgumentException("Unknown role " + id));
    }

    /** A short hash of the system prompt, recorded with every plan and draft. */
    public String promptVersion(String id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(systemPrompt(id).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    public AgentConfig config(String id) {
        RoleDefinition role = role(id).orElseThrow(() -> new IllegalArgumentException("Unknown role " + id));
        // The role's own identifier is the source of its reply plans. The blackboard accepts a contribution only
        // from a source the author may use, and shows an agent only evidence from its sources.
        Set<String> sources = new java.util.LinkedHashSet<>(role.contextSources());
        sources.add(role.id());
        return new AgentConfig(role.id(), role.kind(), systemPrompt(id), role.capabilities(), role.keywords(),
                Set.copyOf(sources), role.fallback());
    }

    @Override
    public Optional<AgentConfig> find(String agentId) {
        return role(agentId).map(role -> config(role.id()));
    }

    @Override
    public List<AgentConfig> all() {
        return roles.keySet().stream().map(this::config).toList();
    }

    /**
     * Writes the configurable parts of an existing role back to its file in a folder catalogue: capabilities,
     * keywords, sources and the fallback flag. The prompt is changed by editing its prompt file, so a config whose
     * system prompt differs is refused. A catalogue from the class path is read-only.
     */
    @Override
    public synchronized void save(AgentConfig config) {
        Path target = folder.orElseThrow(() -> new UnsupportedOperationException("The bundled roles are read-only"));
        RoleDefinition old = role(config.id()).orElseThrow(() -> new IllegalArgumentException(
                "Unknown role " + config.id() + "; add a role by adding its JSON and prompt files"));
        if (!systemPrompt(config.id()).equals(config.systemPrompt())) {
            throw new IllegalArgumentException("Change the prompt in its prompt file, not through save");
        }
        RoleDefinition updated = new RoleDefinition(old.id(), old.kind(), old.displayName(), old.prompt(),
                old.roleHints(), config.keywords(), config.capabilities(), config.contextSourceIds(),
                old.requiredFields(), old.rules(), config.fallback(), old.requiresVerifiedSender(),
                old.maxContextCharacters(), old.maxInputCharacters());
        validate(updated);
        if (updated.fallback() && roles.values().stream().anyMatch(role -> role.fallback() && !role.id().equals(old.id()))) {
            throw new IllegalArgumentException("At most one role may be the fallback");
        }
        try {
            json.writeValue(target.resolve(old.id() + ".json").toFile(), updated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        roles.put(old.id(), updated);
    }

    private static void validate(RoleDefinition role) {
        if (role.id() == null || !IDENTIFIER.matcher(role.id()).matches()) {
            throw new IllegalArgumentException("Role identifier " + role.id() + " must be lower-case letters, digits or -");
        }
        if (role.prompt() == null || !FILE_NAME.matcher(role.prompt()).matches()) {
            throw new IllegalArgumentException("Role " + role.id() + " needs a prompt file name such as student.txt");
        }
        if (role.maxContextCharacters() <= 0 || role.maxInputCharacters() <= role.maxContextCharacters()) {
            throw new IllegalArgumentException("Role " + role.id() + " needs a context budget below its input budget");
        }
        if (role.keywords().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Role " + role.id() + " has an empty keyword");
        }
    }

    /**
     * The composer's trusted system prompt (step 12.5): the common rules, the composer's prompt, then the rules of
     * every contributing role, read from this catalogue and never from the board, so a contribution cannot
     * smuggle instructions into the trusted part.
     */
    public String composerSystemPrompt(List<String> contributorIds) {
        return composerSystemPromptFrom(contributorIds);
    }

    /**
     * The same catalogue with another composer prompt, everything else unchanged: for comparing prompt variants
     * one change at a time (step 13, benchmark option {@code --composer-prompt}).
     */
    public RoleCatalog withComposerPrompt(String composerPromptText) {
        if (composerPromptText == null || composerPromptText.isBlank()) {
            throw new IllegalArgumentException("A composer prompt cannot be empty");
        }
        return new RoleCatalog(List.copyOf(roles.values()),
                name -> name.equals(COMPOSER_PROMPT) ? Optional.of(composerPromptText) : promptFiles.apply(name), folder);
    }

    private String composerSystemPromptFrom(List<String> contributorIds) {
        StringBuilder prompt = new StringBuilder(commonPrompt.strip()).append("\n\n").append(composerPrompt.strip());
        boolean any = false;
        for (String id : contributorIds) {
            RoleDefinition role = roles.get(id);
            if (role == null) {
                continue;
            }
            for (String rule : role.rules()) {
                prompt.append(any ? "" : "\n\nRules of the contributing roles:").append("\n- [").append(role.id())
                        .append("] ").append(rule.strip());
                any = true;
            }
        }
        return prompt.append('\n').toString();
    }

    private static String compose(String common, String rolePrompt, List<String> rules) {
        StringBuilder prompt = new StringBuilder(common.strip()).append("\n\n").append(rolePrompt.strip());
        if (!rules.isEmpty()) {
            prompt.append("\n\nRules for this role:");
            rules.forEach(rule -> prompt.append("\n- ").append(rule.strip()));
        }
        return prompt.append('\n').toString();
    }

    private static Optional<String> resource(String name) {
        try (InputStream in = RoleCatalog.class.getClassLoader().getResourceAsStream(name)) {
            return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
