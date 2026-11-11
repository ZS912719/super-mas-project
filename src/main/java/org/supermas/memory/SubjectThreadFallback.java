package org.supermas.memory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.supermas.context.IndexedDocument;
import org.supermas.context.ProjectIndexes;
import org.supermas.metadata.EmailThreader;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.Scope;

/**
 * Finds the thread of a received reply that carries no reply headers ({@code In-Reply-To}, {@code References}),
 * which the parser would otherwise start as a new thread. Real mail often drops these headers (all of Enron,
 * protocol B46; two synthetic replies, B47), and then the earlier messages of the conversation are lost (B45).
 *
 * <p>The fallback is restricted, because joining the wrong thread would put one conversation's history into a
 * reply to someone else (owner's decision, 2026-11-11: subject fallback only within the same participants and a
 * time window). An email joins an earlier thread only if all of these hold:
 * <ul>
 *   <li>it has no reply headers and the parser gave it its own thread;
 *   <li>its subject starts with a reply prefix ({@code Re:}, {@code AW:}); forwards never join, since they
 *       usually bring the conversation to someone new;
 *   <li>an earlier received message of the same project has the same subject without prefixes, at most
 *       {@link #WINDOW} older and not newer;
 *   <li>every participant (sender and recipients) of the new email took part in that earlier message, so
 *       nobody can be shown history of a conversation they were not part of;
 *   <li>all such earlier messages belong to one thread; when they belong to several, nothing is joined.
 * </ul>
 * The project's own index is searched, so a thread of another project can never be chosen.
 */
public final class SubjectThreadFallback {
    /** How much older the earlier message may be. */
    public static final Duration WINDOW = Duration.ofDays(30);
    /** Reply prefixes only; forward prefixes (Fw, Fwd, Tr, WG) are left out on purpose. */
    private static final Pattern REPLY_PREFIX = Pattern.compile("^\\s*(re|aw)\\s*(\\[\\d+])?\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s]+>");
    private static final int MAX_CANDIDATES = 200;

    private final ProjectIndexes indexes;

    public SubjectThreadFallback(ProjectIndexes indexes) {
        this.indexes = indexes;
    }

    /** The email, with the earlier thread as its scope when the rules above find exactly one; else unchanged. */
    public Email resolve(Email email) {
        if (email.scope() == null || !email.id().equals(email.scope().threadId()) || hasReplyHeaders(email.metadata())
                || email.subject() == null || !REPLY_PREFIX.matcher(email.subject()).find()
                || email.receivedAt() == null || email.sender() == null) {
            return email;
        }
        Set<String> participants = participants(email);
        Instant earliest = email.receivedAt().minus(WINDOW);
        List<IndexedDocument> candidates = indexes.forProject(email.scope().projectId())
                .receivedWithBaseSubject(EmailThreader.baseSubject(email.subject()), MAX_CANDIDATES);
        Set<String> threads = new LinkedHashSet<>();
        for (IndexedDocument earlier : candidates) {
            if (!earlier.id().equals(email.id()) && !earlier.recordedAt().isAfter(email.receivedAt())
                    && !earlier.recordedAt().isBefore(earliest) && earlier.participants().containsAll(participants)) {
                threads.add(earlier.scope().threadId());
            }
        }
        if (threads.size() != 1) {
            return email;
        }
        return new Email(email.id(), new Scope(email.scope().projectId(), threads.iterator().next()), email.sender(),
                email.recipients(), email.subject(), email.body(), email.receivedAt(), email.metadata());
    }

    /** Lower-case addresses of the sender and the recipients. */
    public static Set<String> participants(Email email) {
        Set<String> addresses = new LinkedHashSet<>();
        if (email.sender() != null && !email.sender().isBlank()) {
            addresses.add(email.sender().trim().toLowerCase(Locale.ROOT));
        }
        if (email.recipients() != null) {
            email.recipients().stream().filter(address -> address != null && !address.isBlank())
                    .forEach(address -> addresses.add(address.trim().toLowerCase(Locale.ROOT)));
        }
        return addresses;
    }

    private static boolean hasReplyHeaders(Map<String, String> metadata) {
        if (metadata == null) {
            return false;
        }
        for (String name : new String[] {"In-Reply-To", "References"}) {
            String value = metadata.get(name);
            if (value != null && MESSAGE_ID.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }
}
