package org.supermas.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.supermas.context.IndexedDocument;
import org.supermas.context.ProjectIndex;
import org.supermas.context.ProjectIndexes;
import org.supermas.metadata.LanguageDetector;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Scope;

/**
 * Loads trusted reference documents, such as policies, a course catalogue or a FAQ, into the project indexes.
 * The folder holds {@code sources.json} and one subfolder per source:
 *
 * <pre>
 * knowledge/
 *   sources.json      [{"id": "exam-rules", "project": "uni", "readers": ["student", "teacher"]}]
 *   exam-rules/       *.md and *.txt files
 * </pre>
 *
 * <p>Who may read a source is written here, by whoever maintains the knowledge, and nowhere else. Text is
 * still treated as untrusted when it reaches a model, like every other piece of evidence. Reloading a source
 * replaces all its documents, so a deleted file disappears.
 */
public final class KnowledgeLoader {
    /** Larger files are skipped and counted, so one huge file cannot fill every context budget. */
    public static final int MAX_CHARACTERS = 200_000;
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final String MANIFEST = "sources.json";

    /** What a load did. {@code sources} is what the retriever needs as its trusted source list. */
    public record Report(Map<String, ContextSource> sources, int documents, List<String> skipped) { }

    private final ProjectIndexes indexes;
    private final ObjectMapper json = new ObjectMapper();
    private final LanguageDetector languages = new LanguageDetector();

    public KnowledgeLoader(ProjectIndexes indexes) {
        this.indexes = indexes;
    }

    public Report load(Path folder) {
        Path root = real(folder);
        Map<String, ContextSource> sources = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        int documents = 0;
        JsonNode manifest = read(root.resolve(MANIFEST));
        if (!manifest.isArray()) {
            throw new IllegalArgumentException(MANIFEST + " must be a list of sources");
        }
        for (JsonNode entry : manifest) {
            String id = text(entry, "id");
            String project = text(entry, "project");
            if (!IDENTIFIER.matcher(id).matches()) {
                throw new IllegalArgumentException("Source identifier " + id + " must be lower-case letters, digits or -");
            }
            if (sources.containsKey(id)) {
                throw new IllegalArgumentException("Source " + id + " is declared twice");
            }
            Set<String> readers = new LinkedHashSet<>();
            entry.path("readers").forEach(reader -> readers.add(reader.asText()));
            Path sourceFolder = root.resolve(id);
            sources.put(id, new ContextSource(id, project, sourceFolder.toUri(), Set.copyOf(readers)));
            ProjectIndex index = indexes.forProject(project);
            index.deleteSource(id);
            List<IndexedDocument> loaded = new ArrayList<>();
            for (Path file : files(root, sourceFolder)) {
                Optional<IndexedDocument> document = document(project, id, sourceFolder, file, readers);
                if (document.isPresent()) {
                    loaded.add(document.get());
                } else {
                    skipped.add(id + "/" + sourceFolder.relativize(file));
                }
            }
            index.addAll(loaded);
            index.commit();
            documents += loaded.size();
        }
        return new Report(Map.copyOf(sources), documents, List.copyOf(skipped));
    }

    /** Regular .md and .txt files inside the source folder, in a stable order. Links are not followed. */
    private static List<Path> files(Path root, Path sourceFolder) {
        if (!Files.isDirectory(sourceFolder, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(sourceFolder)) {
            return walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".md") || name.endsWith(".txt");
                    })
                    .filter(path -> real(path).startsWith(root))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<IndexedDocument> document(String project, String sourceId, Path sourceFolder, Path file,
                                               Set<String> readers) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.length() > MAX_CHARACTERS || content.isBlank()) {
                return Optional.empty();
            }
            String relative = sourceFolder.relativize(file).toString().replace('\\', '/');
            return Optional.of(new IndexedDocument("kb:" + sourceId + ":" + relative,
                    new Scope(project, "kb:" + sourceId), sourceId, Optional.empty(), languages.detect(content),
                    title(content, file), content, readers, Set.of(), Files.getLastModifiedTime(file).toInstant()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** The first Markdown heading, else the file name without its extension. */
    private static String title(String content, Path file) {
        for (String line : content.split("\n", 20)) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        String name = file.getFileName().toString();
        return name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
    }

    private JsonNode read(Path file) {
        try {
            return json.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private static String text(JsonNode entry, String field) {
        String value = entry.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Every source needs a non-empty " + field);
        }
        return value;
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
