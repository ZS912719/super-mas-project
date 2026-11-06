package org.supermas.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/** Exactly one index per project, in memory or on disk. A project's data never shares an index with another's. */
public final class ProjectIndexes implements AutoCloseable {
    private final Optional<Path> root;
    private final Map<String, ProjectIndex> indexes = new ConcurrentHashMap<>();

    private ProjectIndexes(Optional<Path> root) {
        this.root = root;
    }

    public static ProjectIndexes inMemory() {
        return new ProjectIndexes(Optional.empty());
    }

    public static ProjectIndexes onDisk(Path root) {
        return new ProjectIndexes(Optional.of(root));
    }

    public ProjectIndex forProject(String projectId) {
        return indexes.computeIfAbsent(projectId, id -> new ProjectIndex(id, open(id)));
    }

    private Directory open(String projectId) {
        try {
            if (root.isEmpty()) {
                return new ByteBuffersDirectory();
            }
            Path folder = root.get().resolve(folderName(projectId));
            Files.createDirectories(folder);
            return FSDirectory.open(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A hash, so that a project identifier such as {@code ../other} can never escape the root or collide. */
    static String folderName(String projectId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(projectId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    @Override
    public void close() throws IOException {
        for (ProjectIndex index : indexes.values()) {
            index.close();
        }
    }
}
