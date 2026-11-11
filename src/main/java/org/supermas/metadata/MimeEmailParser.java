package org.supermas.metadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Scope;

/**
 * Parses a raw RFC 5322 message into an {@link Email}. This is the MetadataAgent's own parser and
 * handles only structure: headers, MIME decoding and the plain-text body. It does no model work.
 *
 * <p>Parsing is language-agnostic, so it runs the same for English and French mail even while the
 * project is scoped to English.
 *
 * <p>Two fields are only provisional here, because one message cannot settle them alone:
 * <ul>
 *   <li>{@code scope.projectId} is supplied by the caller, since a message does not name its project.
 *   <li>{@code scope.threadId} is the first referenced id, else this message's id. Step 1.3 computes
 *       the authoritative thread root from all messages, so it may overwrite this.
 * </ul>
 * The body keeps quotes and signatures; removing them is step 1.4.
 */
public final class MimeEmailParser {
    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s]+>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private final DefaultMessageBuilder builder;

    public MimeEmailParser() {
        this.builder = new DefaultMessageBuilder();
        // Enron and real mail contain malformed headers; permissive parsing keeps them usable.
        this.builder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
    }

    public Email parse(byte[] raw, String projectId) throws EmailParseException {
        Message message;
        try {
            message = builder.parseMessage(new ByteArrayInputStream(raw));
        } catch (IOException | RuntimeException e) {
            throw new EmailParseException("Could not parse message", e);
        }
        try {
            String messageId = firstId(rawHeader(message, "Message-ID")).orElseGet(() -> syntheticId(raw));
            String threadId = firstId(rawHeader(message, "References"))
                    .or(() -> firstId(rawHeader(message, "In-Reply-To")))
                    .orElse(messageId);
            return new Email(messageId, new Scope(projectId, threadId), sender(message),
                    recipients(message), message.getSubject(), body(message), receivedAt(message),
                    metadata(message));
        } catch (RuntimeException e) {
            throw new EmailParseException("Could not read message", e);
        } finally {
            message.dispose();
        }
    }

    private static String sender(Message message) {
        MailboxList from = message.getFrom();
        return from == null || from.isEmpty() ? null : lower(from.get(0));
    }

    private static List<String> recipients(Message message) {
        List<String> recipients = new ArrayList<>();
        addAll(recipients, message.getTo());
        addAll(recipients, message.getCc());
        return recipients;
    }

    private static void addAll(List<String> target, AddressList addresses) {
        if (addresses != null) {
            for (Mailbox mailbox : addresses.flatten()) {
                target.add(lower(mailbox));
            }
        }
    }

    private static Instant receivedAt(Message message) {
        return message.getDate() == null ? null
                : message.getDate().toInstant().truncatedTo(ChronoUnit.SECONDS);
    }

    /** Prefers the plain-text body; falls back to stripped HTML, then to an empty string. */
    private static String body(Entity entity) {
        return firstTextOfType(entity, "text/plain")
                .or(() -> firstTextOfType(entity, "text/html").map(MimeEmailParser::stripHtml))
                .orElse("");
    }

    private static Optional<String> firstTextOfType(Entity entity, String mimeType) {
        Body content = entity.getBody();
        if (content instanceof Multipart multipart) {
            for (Entity part : multipart.getBodyParts()) {
                Optional<String> found = firstTextOfType(part, mimeType);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (content instanceof TextBody text && mimeType.equalsIgnoreCase(entity.getMimeType())) {
            return Optional.of(read(text));
        }
        return Optional.empty();
    }

    private static String read(TextBody text) {
        StringBuilder out = new StringBuilder();
        try (Reader reader = text.getReader()) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                out.append(buffer, 0, count);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    private static String stripHtml(String html) {
        return HTML_TAG.matcher(html).replaceAll(" ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
    }

    private static Map<String, String> metadata(Message message) {
        Map<String, String> metadata = new LinkedHashMap<>();
        // List and automation headers tell whether a reply is needed (ReplyNeedClassifier).
        for (String name : new String[] {"In-Reply-To", "References", "Content-Type", "List-Id", "List-Unsubscribe",
                "Precedence", "Auto-Submitted"}) {
            String value = rawHeader(message, name);
            if (value != null) {
                metadata.put(name, value);
            }
        }
        return metadata;
    }

    private static String rawHeader(Message message, String name) {
        Field field = message.getHeader() == null ? null : message.getHeader().getField(name);
        return field == null ? null : field.getBody();
    }

    private static Optional<String> firstId(String header) {
        if (header == null) {
            return Optional.empty();
        }
        Matcher matcher = MESSAGE_ID.matcher(header);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private static String lower(Mailbox mailbox) {
        return mailbox.getAddress() == null ? null : mailbox.getAddress().toLowerCase(Locale.ROOT);
    }

    private static String syntheticId(byte[] raw) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return "<synthetic-" + hex + "@local.invalid>";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK must provide SHA-256", e);
        }
    }
}
