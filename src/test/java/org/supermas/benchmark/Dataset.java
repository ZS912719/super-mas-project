package org.supermas.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** A folder of NAME.eml files, each with labels in NAME.gold.json. */
public record Dataset(List<Case> cases, String sha256) {
    private static final String EMAIL_SUFFIX = ".eml";
    private static final String GOLD_SUFFIX = ".gold.json";

    public record Case(String id, byte[] rawEmail, Extraction gold) { }

    public static Dataset load(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing.sorted().toList();
        }
        MessageDigest digest = sha256Digest();
        ObjectMapper mapper = new ObjectMapper();
        List<Case> cases = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(file));
            if (name.endsWith(EMAIL_SUFFIX)) {
                String id = name.substring(0, name.length() - EMAIL_SUFFIX.length());
                Path goldFile = directory.resolve(id + GOLD_SUFFIX);
                if (!Files.exists(goldFile)) {
                    throw new IOException("Missing gold labels for " + name);
                }
                cases.add(new Case(id, Files.readAllBytes(file),
                        mapper.readValue(goldFile.toFile(), Extraction.class)));
            } else if (name.endsWith(GOLD_SUFFIX)) {
                String id = name.substring(0, name.length() - GOLD_SUFFIX.length());
                if (!Files.exists(directory.resolve(id + EMAIL_SUFFIX))) {
                    throw new IOException("Missing email for " + name);
                }
            } else {
                throw new IOException("Unexpected file in dataset: " + name);
            }
        }
        if (cases.isEmpty()) {
            throw new IOException("Dataset is empty: " + directory);
        }
        return new Dataset(List.copyOf(cases), HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK must provide SHA-256", e);
        }
    }
}
