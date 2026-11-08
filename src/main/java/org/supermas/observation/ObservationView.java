package org.supermas.observation;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.supermas.agent.collaboration.RequestEntry;
import org.supermas.agent.role.Contribution;
import org.supermas.blackboard.persistence.codec.JsonCodec;
import org.supermas.control.ControlEntry;
import org.supermas.model.Domain.*;
import static org.supermas.observation.ObservationSource.*;

/** Allowlisted metadata projection. Never serialize a domain snapshot or a frozen agent configuration. */
public final class ObservationView {
    private final Capture capture;
    private final BlackboardSnapshot state;
    private final boolean diagnostics;
    private final Map<String, Object> envelope;

    public ObservationView(Capture capture, boolean diagnostics) {
        this.capture = capture;
        this.state = capture.latest().snapshot();
        this.diagnostics = diagnostics;
        envelope = row("board", capture.board(), "databaseId", capture.databaseId(),
                "sourceRevision", capture.latest().revision(), "committedAt", capture.latest().committedAt().toString(),
                "observedAt", capture.observedAt().toString(), "observedAtMs", capture.observedAt().toEpochMilli(),
                "historyTruncated", capture.historyTruncated(), "historyFromRevision", capture.history().get(0).revision());
    }

    public Map<String, Object> render(String agent, Instant from, Instant to) {
        Map<String, Object> result = new LinkedHashMap<>(envelope);
        result.put("overview", List.of(stamp(row("status", state.status(), "draftVersion",
                state.drafts().stream().mapToInt(Draft::version).max().orElse(0), "taskCount", state.tasks().size(),
                "failedTaskCount", state.tasks().stream().filter(t -> t.status() == TaskStatus.FAILED).count(),
                "failedAttemptCount", state.executions().stream().filter(e -> e.status() == TaskStatus.FAILED).count(),
                "mailboxApproval", "UNKNOWN", "mailboxSending", "UNKNOWN", "collectionStatus", "COLLECTED"))));
        result.put("experts", experts(agent));
        result.put("events", events(agent, from, to));
        result.put("timeline", timeline(agent, from, to));
        graph(result, agent);
        return result;
    }

    private List<Map<String, Object>> experts(String filter) {
        Set<String> agents = new TreeSet<>();
        state.tasks().forEach(task -> task.agentId().ifPresent(agents::add));
        state.drafts().forEach(draft -> agents.add(draft.agentId()));
        state.routing().ifPresent(routing -> routing.candidates().forEach(candidate -> agents.add(candidate.agentId())));
        Map<String, ControlEntry.Rated> ratings = new HashMap<>();
        state.evidence().forEach(evidence -> ControlEntry.from(evidence).ifPresent(control ->
                control.todo().forEach(rated -> { agents.add(rated.agentId()); ratings.put(rated.agentId(), rated); })));
        Map<String, Contribution> contributions = contributions();
        contributions.values().forEach(contribution -> agents.add(contribution.agentId()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String agent : agents) {
            if (!matches(filter, agent)) continue;
            List<Task> tasks = state.tasks().stream().filter(task -> task.agentId().equals(Optional.of(agent))).toList();
            String current = tasks.stream().anyMatch(task -> task.status() == TaskStatus.RUNNING) ? "RUNNING_RECORDED"
                    : tasks.stream().anyMatch(task -> task.status() == TaskStatus.PENDING) ? "PENDING" : "UNKNOWN";
            ControlEntry.Rated rating = ratings.get(agent);
            AgentAssessment assessment = state.routing().stream().flatMap(routing -> routing.candidates().stream())
                    .filter(candidate -> candidate.agentId().equals(agent)).findFirst().orElse(null);
            Double score = null;
            if (rating != null) score = rating.estimate();
            else if (assessment != null) score = assessment.score();
            String why = rating != null ? rating.why() : assessment == null ? "" : assessment.reason();
            ExecutionRecord last = null;
            Set<String> taskIds = new HashSet<>();
            tasks.forEach(task -> taskIds.add(task.id()));
            for (ExecutionRecord execution : state.executions()) {
                if (taskIds.contains(execution.taskId())) last = execution;
            }
            result.add(stamp(row("agent", agent, "currentExecution", current,
                    "assessment", score == null ? "NOT_EVALUATED" : rating == null || rating.eligible() == null
                            ? "SCORED_ELIGIBILITY_UNKNOWN" : rating.eligible() ? "SUITABLE_AT_ASSESSMENT" : "NOT_SUITABLE_AT_ASSESSMENT", "score", score,
                    "assessmentSource", rating != null ? "control.todo" : assessment == null ? "NONE" : "routing.candidates",
                    "reason", reason(why), "contributionCount", contributions.values().stream()
                            .filter(contribution -> contribution.agentId().equals(agent)).count(),
                    "lastExecutionResult", last == null ? "UNKNOWN" : last.status(),
                    "lastDurationMs", last == null ? null : Duration.between(last.startedAt(), last.finishedAt()).toMillis(),
                    "lastFailureKind", last == null ? "UNKNOWN" : last.failure().map(failure -> failure.kind().name()).orElse("NONE"),
                    "lastFailureReason", last == null ? "UNKNOWN" : reason(last.failure().map(Failure::message).orElse("")),
                    "usedByComposer", "UNKNOWN", "requestAnswered", "UNKNOWN")));
        }
        return bounded(result);
    }

    private List<Map<String, Object>> events(String filter, Instant from, Instant to) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < state.executions().size(); index++) {
            ExecutionRecord execution = state.executions().get(index);
            Task task = task(execution.taskId());
            String agent = task == null ? "UNKNOWN" : task.agentId().orElse("UNKNOWN");
            if (!matches(filter, agent) || !between(execution.finishedAt(), from, to)) continue;
            List<Attempt> attempts = capture.attempts().stream().filter(attempt -> attempt.task().equals(execution.taskId())
                    && attempt.startedAt().equals(execution.startedAt())).toList();
            result.add(stamp(row("id", capture.board() + ":execution:" + index, "kind", "EXECUTION",
                    "eventTime", execution.finishedAt().toString(), "time", execution.finishedAt().toEpochMilli(),
                    "task", execution.taskId(), "attempt", attempts.size() == 1 ? attempts.get(0).id() : "UNKNOWN",
                    "agent", agent, "action", task == null ? "UNKNOWN" : task.action(),
                    "reason", reason(execution.selectionReason()), "result", execution.status(),
                    "durationMs", Duration.between(execution.startedAt(), execution.finishedAt()).toMillis(),
                    "failureKind", execution.failure().map(failure -> failure.kind().name()).orElse("NONE"),
                    "failureReason", reason(execution.failure().map(Failure::message).orElse("")),
                    "modelCallResult", "UNKNOWN", "taskCompletionAccepted", true,
                    "outputAccepted", "UNKNOWN", "retryable", execution.failure().map(Failure::retryable).orElse(false))));
        }
        Set<String> seen = new HashSet<>();
        Set<String> cancellations = new HashSet<>();
        for (Revision revision : capture.history()) {
            for (Task task : revision.snapshot().tasks()) {
                if (task.status() == TaskStatus.CANCELLED && cancellations.add(task.id())
                        && matches(filter, task.agentId().orElse("UNKNOWN")) && between(revision.committedAt(), from, to))
                    result.add(stamp(row("id", capture.board() + ":cancelled:" + task.id(), "kind", "TASK_STATUS",
                            "eventTime", null, "time", revision.committedAt().toEpochMilli(), "firstObservedRevision", revision.revision(),
                            "task", task.id(), "attempt", "NONE", "agent", task.agentId().orElse("UNKNOWN"),
                            "action", task.action(), "reason", reason(task.reason()), "result", "CANCELLED")));
            }
            for (Evidence evidence : revision.snapshot().evidence()) {
                if (!seen.add(evidence.id())) continue;
                ControlEntry.from(evidence).ifPresent(control -> {
                    if (matches(filter, control.agentId()) && between(revision.committedAt(), from, to))
                        result.add(stamp(row("id", capture.board() + ":control:" + control.round(), "kind", "CONTROL",
                                "eventTime", null, "time", revision.committedAt().toEpochMilli(),
                                "firstObservedRevision", revision.revision(), "task", "UNKNOWN", "attempt", "UNKNOWN",
                                "agent", control.agentId(), "action", control.action(), "reason", reason(control.reason()),
                                "focus", control.focus(), "rating", control.rating(), "result", "SELECTED",
                                "modelCallResult", "UNKNOWN", "outputAccepted", "UNKNOWN")));
                });
            }
        }
        return bounded(result);
    }

    private List<Map<String, Object>> timeline(String filter, Instant from, Instant to) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExecutionRecord execution : state.executions()) {
            Task task = task(execution.taskId());
            if (task == null) continue;
            String agent = task.agentId().orElse("UNKNOWN");
            interval(result, filter, agent, execution.startedAt(), execution.finishedAt(), "RUNNING", task.id(), from, to);
        }
        // Snapshot commit boundaries prove pending intervals only for adjacent retained versions.
        // Terminal outcomes are points in events, never prolonged into a current success/failure interval.
        for (int index = 0; index + 1 < capture.history().size(); index++) {
            Revision before = capture.history().get(index), after = capture.history().get(index + 1);
            if (after.revision() != before.revision() + 1) continue;
            before.snapshot().tasks().stream().filter(task -> task.status() == TaskStatus.PENDING).forEach(task ->
                    interval(result, filter, task.agentId().orElse("UNKNOWN"), before.committedAt(), after.committedAt(),
                            "PENDING", task.id(), from, to));
        }
        List<Map<String, Object>> disjoint = disjointIntervals(bounded(result));
        // Grafana inserts nulls at interval ends, so rows must be disjoint and chronological.
        disjoint.sort(Comparator.<Map<String, Object>>comparingLong(row -> ((Number) row.get("start")).longValue())
                .thenComparingLong(row -> ((Number) row.get("end")).longValue()));
        return bounded(disjoint);
    }

    private List<Map<String, Object>> disjointIntervals(List<Map<String, Object>> intervals) {
        Map<String, List<Map<String, Object>>> byAgent = new TreeMap<>();
        for (Map<String, Object> interval : intervals)
            byAgent.computeIfAbsent((String) interval.get("agent"), key -> new ArrayList<>()).add(interval);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : byAgent.entrySet()) {
            TreeSet<Long> boundaries = new TreeSet<>();
            for (var interval : entry.getValue()) {
                boundaries.add(((Number) interval.get("start")).longValue());
                boundaries.add(((Number) interval.get("end")).longValue());
            }
            List<Long> times = new ArrayList<>(boundaries);
            for (int index = 0; index + 1 < times.size(); index++) {
                long start = times.get(index), end = times.get(index + 1);
                List<Map<String, Object>> covering = entry.getValue().stream().filter(interval ->
                        ((Number) interval.get("start")).longValue() <= start
                        && ((Number) interval.get("end")).longValue() >= end).toList();
                if (covering.isEmpty()) continue;
                List<Object> states = covering.stream().map(interval -> interval.get("state")).distinct().toList();
                List<Object> tasks = covering.stream().map(interval -> interval.get("task")).distinct().toList();
                // Lease start and snapshot commit can disagree by milliseconds. Preserve that ambiguity.
                result.add(stamp(row("agent", entry.getKey(), "start", start, "end", end,
                        "state", states.size() == 1 ? states.get(0) : "OVERLAPPING_RECORDS",
                        "task", tasks.size() == 1 ? tasks.get(0) : null, "tasks", tasks)));
            }
        }
        return result;
    }

    private void interval(List<Map<String, Object>> rows, String filter, String agent, Instant start, Instant end,
                          String status, String task, Instant from, Instant to) {
        if (!matches(filter, agent) || !end.isAfter(start) || !end.isAfter(from) || !start.isBefore(to)) return;
        rows.add(stamp(row("agent", agent, "start", Math.max(start.toEpochMilli(), from.toEpochMilli()),
                "end", Math.min(end.toEpochMilli(), to.toEpochMilli()), "state", status, "task", task)));
    }

    private void graph(Map<String, Object> result, String filter) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<Integer> requests = new HashSet<>();
        RequestEntry.all(state.evidence()).forEach(request -> {
            requests.add(request.index()); node(nodes, "request:" + request.index(), "Request " + request.index(), "request");
        });
        Map<String, Contribution> contributions = contributions();
        for (Contribution contribution : contributions.values()) {
            if (!matches(filter, contribution.agentId())) continue;
            String author = "agent:" + contribution.agentId(), item = "contribution:" + contribution.id();
            node(nodes, author, contribution.agentId(), "expert"); node(nodes, item, contribution.id(), "contribution");
            edge(edges, author, item, "authored");
            contribution.covers().stream().filter(requests::contains).forEach(index ->
                    edge(edges, item, "request:" + index, "declares coverage; not proof of answer"));
            for (Contribution.Excerpt excerpt : contribution.excerpts()) {
                // An identifier reference is evidence of a declared dependency; the excerpt text stays private.
                if (state.evidence().stream().anyMatch(evidence -> evidence.id().equals(excerpt.evidenceId()))) {
                    String id = "evidence:" + excerpt.evidenceId();
                    node(nodes, id, excerpt.evidenceId(), "evidence"); edge(edges, item, id, "declares excerpt");
                }
            }
        }
        for (Draft draft : state.drafts()) {
            if (!matches(filter, draft.agentId())) continue;
            String author = "agent:" + draft.agentId(), id = "draft:" + draft.version();
            node(nodes, author, draft.agentId(), "draft author");
            node(nodes, id, "Draft v" + draft.version(), draft.review().name()); edge(edges, author, id, "authored");
        }
        for (Attempt attempt : capture.attempts()) {
            if (!matches(filter, attempt.agent())) continue;
            String author = "agent:" + attempt.agent(), id = "attempt:" + attempt.id();
            node(nodes, author, attempt.agent(), "agent"); node(nodes, id, attempt.id().substring(0, 12), "execution attempt");
            edge(edges, author, id, "claimed task " + attempt.task());
        }
        result.put("nodes", bounded(new ArrayList<>(nodes.values())));
        result.put("edges", bounded(edges));
    }

    private Map<String, Contribution> contributions() {
        Map<String, Contribution> unique = new LinkedHashMap<>();
        state.evidence().forEach(evidence -> Contribution.from(evidence).ifPresent(item -> unique.putIfAbsent(item.id(), item)));
        return unique;
    }

    private void node(Map<String, Map<String, Object>> nodes, String id, String title, String type) {
        nodes.putIfAbsent(id, stamp(row("id", capture.board() + ":" + id, "title", title, "subTitle", type)));
    }

    private void edge(List<Map<String, Object>> edges, String source, String target, String relation) {
        String prefix = capture.board() + ":";
        String id = JsonCodec.hash(prefix + source + "|" + target + "|" + relation);
        if (edges.stream().noneMatch(edge -> id.equals(edge.get("id"))))
            edges.add(stamp(row("id", id, "source", prefix + source, "target", prefix + target, "mainStat", relation)));
    }

    private Task task(String id) { return state.tasks().stream().filter(task -> task.id().equals(id)).findFirst().orElse(null); }
    private String reason(String text) {
        if (text == null || text.isEmpty()) return "NONE";
        return diagnostics ? text.substring(0, Math.min(512, text.length())) : "REDACTED sha256:" + JsonCodec.hash(text);
    }
    private Map<String, Object> stamp(Map<String, Object> row) { row.putAll(envelope); return row; }
    private static boolean matches(String filter, String agent) { return filter.equals("*") || filter.equals(agent); }
    private static boolean between(Instant value, Instant from, Instant to) { return !value.isBefore(from) && !value.isAfter(to); }
    private static <T> List<T> bounded(List<T> rows) {
        if (rows.size() > 1000) throw new IllegalStateException("Observation row limit exceeded; narrow the time range");
        return rows;
    }
    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }
}
