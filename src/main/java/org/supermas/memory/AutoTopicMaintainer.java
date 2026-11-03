package org.supermas.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.supermas.context.TopicRegrouper;
import org.supermas.model.Domain.ConversationMessage;
import org.supermas.model.Domain.MessageKind;

/**
 * Keeps discovered topics current with nothing for the user to do: after every {@code refreshEvery} new received
 * or sent messages in a project, its topics are found again and the index is retagged. Drafts do not count,
 * because they are not mail anyone exchanged.
 *
 * <p>Regrouping runs on the thread that recorded the message, so the call that crosses the threshold takes
 * longer. That is acceptable at mail rates; a real deployment could move it to a background thread.
 */
public final class AutoTopicMaintainer implements ConversationRecorder.Listener {
    private final TopicRegrouper regrouper;
    private final int refreshEvery;
    private final Map<String, Integer> sinceRefresh = new ConcurrentHashMap<>();
    private final Map<String, TopicRegrouper.Result> last = new ConcurrentHashMap<>();

    public AutoTopicMaintainer(TopicRegrouper regrouper, int refreshEvery) {
        if (refreshEvery < 1) {
            throw new IllegalArgumentException("refreshEvery must be at least 1");
        }
        this.regrouper = regrouper;
        this.refreshEvery = refreshEvery;
    }

    @Override
    public void recorded(ConversationMessage message) {
        if (message.kind() != MessageKind.RECEIVED && message.kind() != MessageKind.SENT) {
            return;
        }
        String project = message.scope().projectId();
        if (sinceRefresh.merge(project, 1, Integer::sum) >= refreshEvery) {
            refreshNow(project);
        }
    }

    /** Regroups one project at once, for example at start-up. */
    public TopicRegrouper.Result refreshNow(String projectId) {
        sinceRefresh.put(projectId, 0);
        TopicRegrouper.Result result = regrouper.refresh(projectId);
        last.put(projectId, result);
        return result;
    }

    public Optional<TopicRegrouper.Result> lastRefresh(String projectId) {
        return Optional.ofNullable(last.get(projectId));
    }
}
