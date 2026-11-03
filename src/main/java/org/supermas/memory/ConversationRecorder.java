package org.supermas.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Approval;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.ConversationMessage;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.DraftOrigin;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.ReviewStatus;
import org.supermas.model.Domain.Scope;

/**
 * Turns what happens to an email into conversation history: the received message, each draft version and,
 * only after a confirmed send, the sent message. Each kind stays separate, so a draft is never read back as
 * something that was said.
 *
 * <p>The callers belong to the team: the coordinator on receipt, the board or review service for drafts, and
 * the future mail transport for sends. The checks here stop mistakes by those trusted callers. They cannot
 * prove that an {@link Approval} object came from a real user action, which stays the review service's job.
 */
// ASSUMED-TEAM-DECISION(P10): the coordinator, the review service and the mail transport call this recorder.
// Pending team validation. If rejected: call the repository from wherever the team records these events.
public final class ConversationRecorder {
    /** Identifiers this class creates start with this, and no received email may use it. */
    public static final String INTERNAL_PREFIX = "supermas:";

    /** Told about every newly stored message, for example to regroup topics. */
    @FunctionalInterface
    public interface Listener {
        void recorded(ConversationMessage message);
    }

    /** Proof from the mail system that a message left. Without one, nothing is recorded as sent. */
    public record SendConfirmation(String transportMessageId, Instant confirmedAt) { }

    private final IndexConversationRepository repository;
    private final MessageTagger tagger;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger listenerFailures = new AtomicInteger();

    public ConversationRecorder(IndexConversationRepository repository, MessageTagger tagger) {
        this.repository = repository;
        this.tagger = tagger;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** How many listener calls failed. A failing listener never loses a record. */
    public int listenerFailures() {
        return listenerFailures.get();
    }

    /**
     * Stores the email's new text, without quoted history, so a thread does not repeat itself. Its identifier
     * is the email's, so a context agent can recognize the email it is answering.
     *
     * @return false if the email was already stored
     */
    public boolean onReceived(Email email, Analysis analysis) {
        if (email.id() == null || email.id().isBlank() || email.id().startsWith(INTERNAL_PREFIX)) {
            throw new SecurityException("A received email may not use an empty or internal identifier");
        }
        String text = Optional.ofNullable(analysis.metadata().get(MetadataAgent.KEY_CURRENT_TEXT))
                .orElse(email.body() == null ? "" : email.body());
        ConversationMessage message = new ConversationMessage(email.id(), email.scope(), MessageKind.RECEIVED,
                email.subject(), text, new Provenance(email.id(), "Received email", email.receivedAt()),
                email.receivedAt());
        return store(message, tagger.tags(email.scope(), email.subject(), text, Optional.of(email)));
    }

    /** One record per draft version, of kind generated or user-edited by the draft's origin. */
    public boolean onDraft(BoardId board, Draft draft) {
        MessageKind kind = draft.origin() == DraftOrigin.USER_EDITED ? MessageKind.USER_EDITED_DRAFT
                : MessageKind.GENERATED_DRAFT;
        ConversationMessage message = new ConversationMessage(id(board, draft.version(), kind), board.scope(), kind,
                draft.subject(), draft.body(), new Provenance(draft.agentId(), "Draft version " + draft.version(),
                draft.createdAt()), draft.createdAt());
        return store(message, tagger.tags(board.scope(), draft.subject(), draft.body(), Optional.empty()));
    }

    /**
     * Records the approved version as sent. Refused unless the approval is for this board and exactly this
     * version, the version is approved, and the mail system confirmed the send after the approval.
     */
    public boolean onSentConfirmed(BoardId board, Draft draft, Approval approval, SendConfirmation confirmation) {
        if (!approval.boardId().equals(board)) {
            throw new SecurityException("The approval belongs to another board");
        }
        if (approval.draftVersion() != draft.version() || draft.review() != ReviewStatus.APPROVED) {
            throw new SecurityException("Only the exact approved version can be recorded as sent");
        }
        if (confirmation == null || confirmation.transportMessageId() == null
                || confirmation.transportMessageId().isBlank() || confirmation.confirmedAt() == null) {
            throw new SecurityException("A send must be confirmed by the mail system");
        }
        if (confirmation.confirmedAt().isBefore(approval.approvedAt())) {
            throw new SecurityException("A send cannot be confirmed before its approval");
        }
        ConversationMessage message = new ConversationMessage(id(board, draft.version(), MessageKind.SENT),
                board.scope(), MessageKind.SENT, draft.subject(), draft.body(),
                new Provenance(confirmation.transportMessageId(), "Sent version " + draft.version()
                        + ", confirmed by the mail system", confirmation.confirmedAt()), confirmation.confirmedAt());
        return store(message, tagger.tags(board.scope(), draft.subject(), draft.body(), Optional.empty()));
    }

    private boolean store(ConversationMessage message, Set<String> tags) {
        boolean stored = repository.appendIfAbsent(message, tags);
        if (stored) {
            for (Listener listener : listeners) {
                try {
                    listener.recorded(message);
                } catch (RuntimeException e) {
                    listenerFailures.incrementAndGet();
                }
            }
        }
        return stored;
    }

    /** Board identifiers are part of the record identifier, so equal versions on two boards never collide. */
    static String id(BoardId board, int version, MessageKind kind) {
        Scope scope = board.scope();
        return INTERNAL_PREFIX + scope.threadId() + ":" + board.taskId() + ":v" + version + ":" + kind.name();
    }
}
