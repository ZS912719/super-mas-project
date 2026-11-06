package org.supermas.model;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** API data contracts only. Validation, defensive copying and storage formats remain to be implemented. */
public final class Domain {
    private Domain() { }

    /** Both identifiers participate in isolation, including when thread identifiers coincide. */
    public record Scope(String projectId, String threadId) { }
    public record BoardId(Scope scope, String taskId) { }

    public record Email(String id, Scope scope, String sender, List<String> recipients,
                        String subject, String body, Instant receivedAt, Map<String, String> metadata) { }
    public record Analysis(List<String> keywords, List<String> initialTags, Map<String, String> metadata) { }

    public enum EvidenceKind { ORIGINAL_FACT, RETRIEVED_EVIDENCE, MODEL_INFERENCE }
    public record Provenance(String sourceId, String description, Instant recordedAt) { }
    public record Evidence(String id, Scope scope, EvidenceKind kind, Provenance provenance,
                           String text, Set<String> permittedAgentIds) { }

    /** Prompts and context permissions are configuration, not coordinator classification branches. */
    public record AgentConfig(String id, String role, String systemPrompt, Set<String> capabilities,
                              List<String> keywords, Set<String> contextSourceIds, boolean fallback) { }
    public record AgentAssessment(String agentId, double score, boolean sufficientContext, String reason) { }
    public enum Match { CLEAR, AMBIGUOUS, NONE }
    public record RoutingDecision(Match match, Optional<String> selectedAgentId,
                                  List<AgentAssessment> candidates, String reason) { }

    // Prototype branch, step 12.1: CONTRIBUTE is a specialist posting a partial reply plan for some requests.
    public enum Action { ROUTE, RETRIEVE_CONTEXT, RECLASSIFY, GENERATE, REVISE, PRESENT_DRAFT, STOP, CONTRIBUTE }
    public enum TaskStatus { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED }
    // ASSUMED-TEAM-DECISION(P2): a task can carry a structured information need. Pending team validation.
    // If rejected: revert this commit and carry the need in a Context package task object.
    /** What further retrieval should look for. Values come from fixed lists, never from email text. */
    public record InformationNeed(Set<String> fields, List<String> searchTerms) { }
    public record Task(String id, BoardId boardId, Action action, Optional<String> agentId,
                       TaskStatus status, String reason, Optional<InformationNeed> need) {
        /** A task with no information need, so existing callers keep compiling. */
        public Task(String id, BoardId boardId, Action action, Optional<String> agentId,
                    TaskStatus status, String reason) {
            this(id, boardId, action, agentId, status, reason, Optional.empty());
        }
    }
    public record ExecutionRecord(String taskId, String selectionReason, TaskStatus status,
                                  Instant startedAt, Instant finishedAt, String summary,
                                  Optional<Failure> failure) { }
    public record NextAction(Action action, Optional<String> agentId, String reason) { }
    public record ExecutionLimits(int maxRounds, int maxRetries, int maxGenerationAttempts,
                                  int maxInputCharacters, int maxContextCharacters) { }
    public record BudgetUsage(int rounds, int retries, int generationAttempts) { }
    public enum FailureKind { CONNECTION, TIMEOUT, INVALID_OUTPUT, BUDGET_EXHAUSTED, CONTEXT, STORAGE, AGENT, INTERRUPTED }
    public record Failure(FailureKind kind, String message, boolean retryable) { }

    public enum BoardStatus { PROCESSING, AWAITING_REVIEW, APPROVED, FAILED, STOPPED }
    public enum DraftOrigin { MODEL_GENERATED, USER_EDITED }
    public enum ReviewStatus { PROPOSED, REJECTED, SUPERSEDED, APPROVED }
    /** Explanation is internal UI metadata and must remain separate from the outward subject and body. */
    public record Draft(int version, String subject, String body, String agentId, String explanation,
                        DraftOrigin origin, ReviewStatus review, Instant createdAt) { }
    public record DraftProposal(String subject, String body, String agentId, String explanation) { }
    public record Feedback(int draftVersion, String text, Instant createdAt) { }
    /** Approval covers exactly one draft version. It is not confirmation that a message was sent. */
    public record Approval(BoardId boardId, int draftVersion, Instant approvedAt) { }

    public record BlackboardSnapshot(BoardId id, Email email, Analysis analysis, List<Evidence> evidence,
                                      List<Task> tasks, List<ExecutionRecord> executions, List<Draft> drafts,
                                      List<Feedback> feedback, Optional<RoutingDecision> routing,
                                      BoardStatus status, BudgetUsage budgetUsage, Optional<Failure> failure) { }
    public record AgentOutcome(TaskStatus status, String summary, List<Task> proposedTasks,
                               Optional<Failure> failure) { }

    public enum MessageKind { RECEIVED, GENERATED_DRAFT, USER_EDITED_DRAFT, SENT }
    public record ConversationMessage(String id, Scope scope, MessageKind kind, String subject,
                                      String body, Provenance provenance, Instant recordedAt) { }
    public record HistoryQuery(Set<MessageKind> kinds, int maxMessages) { }

    public record ContextSource(String id, String projectId, URI location, Set<String> permittedAgentIds) { }
    public record ContextQuery(BoardId boardId, String agentId, Set<String> sourceIds,
                               List<String> keywords, boolean supplemental, int maxCharacters) { }
    public record RetrievalResult(List<Evidence> evidence, boolean sufficient, String explanation) { }
    public record ContextSelection(List<Evidence> evidence, int usedCharacters, List<String> omittedSourceIds) { }

    /** Future adapters must enforce local-only endpoints and disable remote redirects. */
    public record ModelConfig(String model, URI endpoint, Duration timeout, int maxOutputCharacters) { }
    /** Trusted instructions and untrusted task data have separate fields by design. */
    public record ModelRequest(String agentId, String systemPrompt, String untrustedData,
                               int maxInputCharacters) { }
    public record ModelResponse(Optional<String> output, Optional<Failure> failure) { }
    public record GenerationResult(Optional<DraftProposal> draft, Optional<Failure> failure) { }
}
