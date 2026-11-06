package org.supermas.metadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.supermas.model.Domain.Email;

/**
 * Assigns a thread id to each email in one project, following RFC 5256:
 * <ol>
 *   <li>link messages whose References / In-Reply-To headers point at each other, then
 *   <li>as a fallback, join messages that share a base subject when at least one is a reply or
 *       forward (real mail, such as Enron, often drops the reference headers).
 * </ol>
 * The thread id is the earliest message's id, matching the field schema and the importer.
 *
 * <p>The subject fallback can over-merge two unrelated threads that share a generic subject once one
 * of them is a reply. It is therefore a constructor flag, so it can be turned off and measured.
 *
 * <p>Callers pass the emails of a single project. Thread ids are not compared across projects, so
 * equal thread ids in different projects stay isolated, as the scope rule requires.
 */
public final class EmailThreader {
    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s]+>");
    private static final Pattern REPLY_PREFIX =
            Pattern.compile("^\\s*(re|fw|fwd|tr|aw|wg)\\s*(\\[\\d+])?\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    private final boolean useSubjectFallback;

    public EmailThreader() {
        this(true);
    }

    public EmailThreader(boolean useSubjectFallback) {
        this.useSubjectFallback = useSubjectFallback;
    }

    /** Returns a map from each email id to its thread id. */
    public Map<String, String> assignThreadIds(List<Email> emails) {
        Map<String, Email> byId = new LinkedHashMap<>();
        for (Email email : emails) {
            byId.putIfAbsent(email.id(), email);
        }
        Map<String, String> parent = new HashMap<>();
        byId.keySet().forEach(id -> parent.put(id, id));

        for (Email email : byId.values()) {
            for (String reference : references(email)) {
                if (byId.containsKey(reference)) {
                    union(parent, email.id(), reference);
                }
            }
        }
        if (useSubjectFallback) {
            linkBySubject(byId, parent);
        }

        Map<String, List<Email>> components = new LinkedHashMap<>();
        for (Email email : byId.values()) {
            components.computeIfAbsent(find(parent, email.id()), key -> new ArrayList<>()).add(email);
        }
        Comparator<Email> byDateThenId = Comparator
                .comparing((Email e) -> e.receivedAt() == null ? java.time.Instant.MAX : e.receivedAt())
                .thenComparing(Email::id);
        Map<String, String> threadIdByMessageId = new LinkedHashMap<>();
        for (List<Email> component : components.values()) {
            String threadId = component.stream().min(byDateThenId).orElseThrow().id();
            for (Email email : component) {
                threadIdByMessageId.put(email.id(), threadId);
            }
        }
        return threadIdByMessageId;
    }

    private void linkBySubject(Map<String, Email> byId, Map<String, String> parent) {
        Map<String, List<String>> byBaseSubject = new LinkedHashMap<>();
        Map<String, Boolean> baseSubjectHasReply = new HashMap<>();
        for (Email email : byId.values()) {
            String base = baseSubject(email.subject());
            if (base.isEmpty()) {
                continue;
            }
            byBaseSubject.computeIfAbsent(base, key -> new ArrayList<>()).add(email.id());
            if (isReplyOrForward(email.subject())) {
                baseSubjectHasReply.put(base, true);
            }
        }
        byBaseSubject.forEach((base, ids) -> {
            if (ids.size() > 1 && baseSubjectHasReply.getOrDefault(base, false)) {
                for (String id : ids) {
                    union(parent, ids.get(0), id);
                }
            }
        });
    }

    private static List<String> references(Email email) {
        List<String> ids = new ArrayList<>();
        for (String name : new String[] {"References", "In-Reply-To"}) {
            String header = email.metadata().get(name);
            if (header != null) {
                Matcher matcher = MESSAGE_ID.matcher(header);
                while (matcher.find()) {
                    ids.add(matcher.group());
                }
            }
        }
        return ids;
    }

    static String baseSubject(String subject) {
        if (subject == null) {
            return "";
        }
        String base = subject.trim();
        String previous;
        do {
            previous = base;
            base = REPLY_PREFIX.matcher(base).replaceFirst("");
        } while (!base.equals(previous));
        return base.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    static boolean isReplyOrForward(String subject) {
        return subject != null && REPLY_PREFIX.matcher(subject).find();
    }

    private static String find(Map<String, String> parent, String node) {
        String root = node;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        String current = node;
        while (!parent.get(current).equals(root)) {
            String next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        parent.put(find(parent, a), find(parent, b));
    }
}
