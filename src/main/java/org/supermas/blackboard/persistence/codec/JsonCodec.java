package org.supermas.blackboard.persistence.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.supermas.blackboard.persistence.StorageException;

public final class JsonCodec {
    private JsonCodec() { }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module()).registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot encode state", e); }
    }

    public static String pretty(Object value) {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot render state", e); }
    }

    public static <T> T read(String json, Class<T> type) {
        try { return MAPPER.readValue(json, type); }
        catch (Exception e) { throw new StorageException("Cannot decode persisted state", e); }
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
