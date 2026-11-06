package org.supermas.agent.collaboration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.supermas.model.Domain.Evidence;
import org.supermas.model.Domain.EvidenceKind;
import org.supermas.model.Domain.Provenance;
import org.supermas.model.Domain.Scope;

/**
 * One request of the email, at the request level of the blackboard (docs/plans/blackboard-control.md). The quote
 * is copied from the email, never rewritten, and stays untrusted data. A general entry stands for the email as a
 * whole when no sentence reads as a request.
 *
 * <p>On the board: a {@code MODEL_INFERENCE} with identifier {@code request:<index>} and this record as JSON
 * ({@code request/v1}). A promoted copy carries a suffix after the index; {@link #from} reads both.
 */
public record RequestEntry(String schema, int index, String quote, boolean general) {
    public static final String SCHEMA = "request/v1";
    public static final String ID_PREFIX = "request:";
    private static final ObjectMapper JSON = new ObjectMapper();

    public RequestEntry {
        if (index < 1) {
            throw new IllegalArgumentException("A request index starts at 1");
        }
        quote = quote == null ? "" : quote;
    }

    public static RequestEntry of(int index, String quote) {
        return new RequestEntry(SCHEMA, index, quote, false);
    }

    public static RequestEntry generalEntry() {
        return new RequestEntry(SCHEMA, 1, "", true);
    }

    public Evidence toEvidence(Scope scope, String author, java.time.Instant at) {
        try {
            return new Evidence(ID_PREFIX + index, scope, EvidenceKind.MODEL_INFERENCE,
                    new Provenance(author, general ? "The email as a whole" : "Request quoted from the email", at),
                    JSON.writeValueAsString(this), Set.of(author));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A request entry is always serializable", e);
        }
    }

    public static Optional<RequestEntry> from(Evidence evidence) {
        if (evidence.kind() != EvidenceKind.MODEL_INFERENCE || !evidence.id().startsWith(ID_PREFIX)) {
            return Optional.empty();
        }
        try {
            RequestEntry entry = JSON.readValue(evidence.text(), RequestEntry.class);
            return SCHEMA.equals(entry.schema()) ? Optional.of(entry) : Optional.empty();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The requests visible in these entries, once each, by index (an original and its copy count once). */
    public static List<RequestEntry> all(Collection<Evidence> evidence) {
        Map<Integer, RequestEntry> byIndex = new TreeMap<>();
        for (Evidence item : evidence) {
            from(item).ifPresent(entry -> byIndex.putIfAbsent(entry.index(), entry));
        }
        return List.copyOf(byIndex.values());
    }
}
