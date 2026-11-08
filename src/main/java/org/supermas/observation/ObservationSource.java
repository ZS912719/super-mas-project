package org.supermas.observation;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.supermas.model.Domain.BlackboardSnapshot;

/** Developer-observer capability: explicitly authorized boards, no commands or lease recovery. */
public interface ObservationSource {
    record Revision(long revision, Instant committedAt, BlackboardSnapshot snapshot) { }
    record Capture(String databaseId, String board, Instant observedAt, List<Revision> history,
                   boolean historyTruncated, List<Attempt> attempts) {
        public Revision latest() { return history.get(history.size() - 1); }
    }
    record Attempt(String id, String task, String agent, Instant startedAt, long inputRevision) { }
    List<String> boards();
    Capture capture(String board);
    Set<String> authorizedBoards();
}
