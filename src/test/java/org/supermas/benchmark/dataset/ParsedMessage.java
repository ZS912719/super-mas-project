package org.supermas.benchmark.dataset;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One raw message parsed with Jakarta Mail. This parser is used only to derive gold facts for the
 * dataset. It is intentionally not the parser the MetadataAgent uses, so the gold values are
 * produced independently of the code under test.
 */
public record ParsedMessage(byte[] raw, String messageId, boolean syntheticId, Instant sentDate,
                            Set<String> references, String sender, List<String> recipients,
                            List<String> warnings) {
    private static final Session SESSION = Session.getInstance(new Properties());
    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s]+>");

    public static ParsedMessage parse(byte[] raw) {
        List<String> warnings = new ArrayList<>();
        MimeMessage message;
        try {
            message = new MimeMessage(SESSION, new ByteArrayInputStream(raw));
        } catch (Exception e) {
            // A message that will not parse at all still keeps its bytes and gets a synthetic id,
            // so it is never silently dropped from the corpus.
            return new ParsedMessage(raw, syntheticId(raw), true, null, Set.of(), null,
                    List.of(), List.of("unparseable: " + e.getMessage()));
        }
        String messageId = header(message, "Message-ID", warnings);
        boolean synthetic = messageId == null;
        if (synthetic) {
            messageId = syntheticId(raw);
            warnings.add("missing Message-ID; synthesized one");
        }
        return new ParsedMessage(raw, messageId, synthetic, sentDate(message, warnings),
                references(message), sender(message, warnings), recipients(message, warnings),
                List.copyOf(warnings));
    }

    private static String header(MimeMessage message, String name, List<String> warnings) {
        try {
            String[] values = message.getHeader(name);
            if (values == null || values.length == 0) {
                return null;
            }
            Matcher matcher = MESSAGE_ID.matcher(values[0]);
            return matcher.find() ? matcher.group() : values[0].trim();
        } catch (Exception e) {
            warnings.add("could not read " + name);
            return null;
        }
    }

    private static Set<String> references(MimeMessage message) {
        Set<String> ids = new LinkedHashSet<>();
        for (String name : new String[] {"References", "In-Reply-To"}) {
            try {
                String[] values = message.getHeader(name);
                if (values != null) {
                    for (String value : values) {
                        Matcher matcher = MESSAGE_ID.matcher(value);
                        while (matcher.find()) {
                            ids.add(matcher.group());
                        }
                    }
                }
            } catch (Exception ignored) {
                // A missing or malformed reference header only means fewer thread links.
            }
        }
        return ids;
    }

    private static Instant sentDate(MimeMessage message, List<String> warnings) {
        try {
            if (message.getSentDate() != null) {
                return message.getSentDate().toInstant().truncatedTo(ChronoUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // Fall through to the warning below.
        }
        warnings.add("missing or unparseable Date");
        return null;
    }

    private static String sender(MimeMessage message, List<String> warnings) {
        try {
            if (message.getFrom() != null && message.getFrom().length > 0
                    && message.getFrom()[0] instanceof InternetAddress address) {
                return address.getAddress().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // Fall through to the warning below.
        }
        warnings.add("missing or unparseable From");
        return null;
    }

    private static List<String> recipients(MimeMessage message, List<String> warnings) {
        List<String> recipients = new ArrayList<>();
        for (Message.RecipientType type : new Message.RecipientType[] {
                Message.RecipientType.TO, Message.RecipientType.CC}) {
            try {
                jakarta.mail.Address[] addresses = message.getRecipients(type);
                if (addresses != null) {
                    for (jakarta.mail.Address address : addresses) {
                        if (address instanceof InternetAddress internet) {
                            recipients.add(internet.getAddress().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } catch (Exception e) {
                warnings.add("could not read " + type + " recipients");
            }
        }
        return recipients;
    }

    private static String syntheticId(byte[] raw) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return "<synthetic-" + hex + "@enron.invalid>";
        } catch (Exception e) {
            throw new IllegalStateException("The JDK must provide SHA-256", e);
        }
    }
}
