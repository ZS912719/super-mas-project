package org.supermas.benchmark;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.supermas.context.ProjectIndexes;
import org.supermas.memory.ConversationRecorder;
import org.supermas.memory.IndexConversationRepository;
import org.supermas.memory.MessageTagger;
import org.supermas.memory.SubjectThreadFallback;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.ContextSource;
import org.supermas.model.Domain.Email;

/**
 * Threads a batch of emails as the prototype does on receipt: in date order, each one resolved by
 * {@link SubjectThreadFallback} against the messages received before it, then recorded by the
 * {@link ConversationRecorder}. In memory; for benchmarks only.
 */
final class ReceiptThreading {
    private ReceiptThreading() { }

    /** Thread id per email id. Emails whose identifier was already received are skipped. */
    static Map<String, String> assign(List<Email> emails) {
        Map<String, String> threads = new LinkedHashMap<>();
        try (ProjectIndexes indexes = ProjectIndexes.inMemory()) {
            IndexConversationRepository repository = new IndexConversationRepository(indexes,
                    project -> Optional.of(new ContextSource("mail", project, URI.create("file:///benchmark"), Set.of("reader"))));
            ConversationRecorder recorder = new ConversationRecorder(repository, MessageTagger.none());
            SubjectThreadFallback fallback = new SubjectThreadFallback(indexes);
            List<Email> ordered = emails.stream().sorted(Comparator
                    .comparing((Email e) -> e.receivedAt() == null ? Instant.MAX : e.receivedAt()).thenComparing(Email::id)).toList();
            for (Email email : ordered) {
                if (threads.containsKey(email.id())) {
                    continue;
                }
                Email resolved = fallback.resolve(email);
                recorder.onReceived(resolved, new Analysis(List.of(), List.of(), Map.of()));
                threads.put(email.id(), resolved.scope().threadId());
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return threads;
    }
}
