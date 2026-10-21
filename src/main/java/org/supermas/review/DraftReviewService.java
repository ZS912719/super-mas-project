package org.supermas.review;

import org.supermas.model.Domain.*;
import java.util.List;

/** Explicit human review only. This interface has no sending operation. */
public interface DraftReviewService {
    List<Draft> versions(BoardId boardId);

    /** Create a new USER_EDITED version, invalidate earlier approval and preserve the original. */
    Draft edit(BoardId boardId, int expectedVersion, String subject, String body);

    /** Reject the current version, save feedback and make revision work available to the coordinator. */
    Feedback reject(BoardId boardId, int expectedVersion, String feedback);

    /** Require an explicit user action for the current version; stale-version approval must fail. */
    Approval approve(BoardId boardId, int expectedVersion);
}
