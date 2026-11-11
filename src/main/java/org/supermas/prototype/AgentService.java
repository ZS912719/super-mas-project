package org.supermas.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.supermas.agent.role.Contribution;
import org.supermas.agent.role.ReplyPlan;
import org.supermas.agent.role.RoleDefinition;
import org.supermas.context.ProjectIndexes;
import org.supermas.llm.LlmSettings;
import org.supermas.metadata.MetadataAgent;
import org.supermas.model.Domain.AgentAssessment;
import org.supermas.model.Domain.BlackboardSnapshot;
import org.supermas.model.Domain.BoardId;
import org.supermas.model.Domain.Draft;
import org.supermas.model.Domain.Email;
import org.supermas.model.Domain.RoutingDecision;
import org.supermas.model.Domain.Scope;

// PROTOTYPE-WIRING: a proposal for the mailbox-to-coordinator integration, which is an open team question.
// The contract is in docs/plans/agent-service-api.md. Replace it with the protocol the team agrees on.
/**
 * Lets the desktop mailbox ask the agents for a reply. One loopback endpoint, {@code POST /v1/reply}, takes the
 * conversation the mailbox shows and returns the specialist's draft, the routing decision and a classification
 * in the mailbox's own JSON shape. Nothing is approved or sent here: approval and sending stay in the mailbox.
 *
 * <p>Usage: {@code AgentService [--knowledge DIR] [--data DIR] [--project ID] [--port N] [--architecture A]
 * [--llm-provider P] [--llm-endpoint URL] [--llm-model NAME]}, with the shared secret in the environment variable
 * {@code AGENT_SERVICE_TOKEN} (at least 32 characters). The model settings are also read from the
 * {@code SUPERMAS_LLM_*} variables ({@link LlmSettings}).
 *
 * <p>The mailbox may show real mail, so the service is stricter than the prototype runner: the online model (a
 * development stand-in) needs a second opt-in, {@code SUPERMAS_LLM_ONLINE_MAIL=yes}, and recording or replaying
 * replies is refused, because recordings would keep replies to real mail on disk.
 *
 * <p>Requests are refused unless they come with the exact loopback {@code Host}, a JSON body of at most
 * {@link #MAX_BODY} bytes and the token in {@code X-SuperMAS-Token}. The token matters because any web page in
 * a local browser can send a request to a loopback port. Requests are handled one at a time, because the
 * coordinator stand-in is sequential. Every email field is untrusted data and only ever reaches the agents.
 */
public final class AgentService implements AutoCloseable {
    public static final String TOKEN_HEADER = "X-SuperMAS-Token";
    public static final int DEFAULT_PORT = 4318;
    public static final int MAX_BODY = 256_000;
    public static final int MAX_MESSAGES = 200;
    public static final int MAX_FIELD = 32_000;
    static final int MIN_TOKEN = 32;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final byte[] token;
    private final String host;
    private final ProjectIndexes indexes;
    private final PrototypeApp.Wiring wiring;
    private final String project;
    private final MetadataAgent metadata = MetadataAgent.rulesOnly();
    private final PrototypeApp.Architecture architecture;

    /** A request the service refuses, with the HTTP status and a message safe to show. */
    static final class Refused extends RuntimeException {
        final int status;

        Refused(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /**
     * @param port 0 for any free port, as in tests
     */
    public AgentService(PrototypeApp.Options options, int port, String token) throws IOException {
        if (token == null || token.length() < MIN_TOKEN) {
            throw new IllegalArgumentException("AGENT_SERVICE_TOKEN must have at least " + MIN_TOKEN + " characters");
        }
        checkModel(options.llm());
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.project = options.project();
        this.architecture = options.architecture();
        Files.createDirectories(options.data());
        this.indexes = ProjectIndexes.onDisk(options.data().resolve("index"));
        this.wiring = PrototypeApp.Wiring.create(indexes, options);
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        this.host = "127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.setExecutor(executor);
    }

    /** Refuses model settings that would send or keep real mail without the user's explicit consent. */
    static void checkModel(LlmSettings llm) {
        if (llm.leavesComputer() && !llm.onlineForMail()) {
            throw new IllegalArgumentException("The online model would send the mailbox's emails to " + llm.endpoint().getHost()
                    + ". To allow it for the mailbox too, also set " + LlmSettings.ONLINE_MAIL + "=yes");
        }
        if (llm.cache().isPresent()) {
            throw new IllegalArgumentException("Recording and replay are for benchmark emails, not the mailbox; drop "
                    + LlmSettings.CACHE);
        }
    }

    public static void main(String[] args) throws Exception {
        Path knowledge = Path.of("src/test/resources/university/knowledge");
        Path data = Path.of("runtime");
        String project = "uni";
        int port = DEFAULT_PORT;
        PrototypeApp.Architecture architecture = PrototypeApp.Architecture.BLACKBOARD;
        java.util.Map<String, String> llmFlags = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + args[i]);
            }
            switch (args[i]) {
                case "--knowledge" -> knowledge = Path.of(args[i + 1]);
                case "--data" -> data = Path.of(args[i + 1]);
                case "--project" -> project = args[i + 1];
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--architecture" -> architecture = PrototypeApp.Architecture.valueOf(args[i + 1].toUpperCase(java.util.Locale.ROOT));
                default -> {
                    if (!LlmSettings.isFlag(args[i])) {
                        throw new IllegalArgumentException("Unknown option " + args[i]);
                    }
                    llmFlags.put(args[i], args[i + 1]);
                }
            }
        }
        LlmSettings llm = LlmSettings.from(System.getenv(), llmFlags);
        AgentService service = new AgentService(new PrototypeApp.Options(null, knowledge, data, project, architecture,
                org.supermas.control.AgendaStrategy.Settings.defaults().minContribution(), llm), port,
                System.getenv("AGENT_SERVICE_TOKEN"));
        Runtime.getRuntime().addShutdownHook(new Thread(service::close));
        service.start();
        System.out.println("Agent service on http://" + service.host() + "/v1/reply (project " + project + ", data "
                + data.toAbsolutePath() + "). Model: " + llm.describe() + ". Ctrl+C stops it.");
    }

    public void start() {
        server.start();
    }

    /** The exact {@code Host} value requests must carry. */
    public String host() {
        return host;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        try {
            indexes.close();
        } catch (IOException e) {
            System.err.println("Index not closed cleanly: " + e.getMessage());
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            int status = 200;
            JsonNode body;
            try {
                body = reply(read(exchange));
            } catch (Refused e) {
                status = e.status;
                body = error(e.getMessage());
            } catch (RuntimeException e) {
                status = 500;
                body = error("The agents could not process this conversation: " + e.getMessage());
            }
            byte[] bytes = JSON.writeValueAsBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    /** Every check that does not need the body's meaning, in the order a refusal is cheapest. */
    private JsonNode read(HttpExchange exchange) throws IOException {
        if (!host.equals(exchange.getRequestHeaders().getFirst("Host"))) {
            throw new Refused(403, "Use http://" + host);
        }
        String supplied = exchange.getRequestHeaders().getFirst(TOKEN_HEADER);
        if (supplied == null || !MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new Refused(401, "Missing or wrong " + TOKEN_HEADER);
        }
        if (!exchange.getRequestURI().getPath().equals("/v1/reply")) {
            throw new Refused(404, "Unknown path");
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            throw new Refused(405, "Use POST");
        }
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.startsWith("application/json")) {
            throw new Refused(415, "JSON content is required");
        }
        byte[] raw;
        try (InputStream in = exchange.getRequestBody()) {
            raw = in.readNBytes(MAX_BODY + 1);
        }
        if (raw.length > MAX_BODY) {
            throw new Refused(413, "The request exceeds " + MAX_BODY + " bytes");
        }
        try {
            return JSON.readTree(raw);
        } catch (IOException e) {
            throw new Refused(400, "The body is not valid JSON");
        }
    }

    /** A conversation message as the mailbox holds it. */
    record Message(String id, String threadId, String sender, String recipient, String subject, String body,
                   Instant date, boolean received) {
        Email email(String project) {
            return new Email(id, new Scope(project, threadId), sender, recipient.isEmpty() ? List.of() : List.of(recipient),
                    subject, body, date, Map.of());
        }
    }

    JsonNode reply(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new Refused(400, "The body must be a JSON object");
        }
        String messageId = text(request, "messageId", true);
        JsonNode list = request.get("messages");
        if (list == null || !list.isArray() || list.isEmpty() || list.size() > MAX_MESSAGES) {
            throw new Refused(400, "messages must be an array of 1 to " + MAX_MESSAGES + " messages");
        }
        List<Message> messages = new ArrayList<>();
        for (JsonNode item : list) {
            messages.add(message(item));
        }
        Message selected = messages.stream().filter(message -> message.id().equals(messageId)).findFirst()
                .orElseThrow(() -> new Refused(400, "messageId is not among the messages"));
        if (!selected.received()) {
            throw new Refused(400, "Only a received message can be answered");
        }
        if (messages.stream().anyMatch(message -> !message.threadId().equals(selected.threadId()))) {
            throw new Refused(400, "All messages must belong to the selected message's thread");
        }
        List<String> notes = new ArrayList<>();
        // Earlier received mail of the thread becomes history. Sent mail does not: SENT is recorded only after a
        // version-specific approval on this side, and the mailbox's approval does not reach the agents yet.
        for (Message message : messages) {
            if (message.received() && !message.id().equals(selected.id()) && message.date().isBefore(selected.date())) {
                Email email = message.email(project);
                try {
                    wiring.recorder.onReceived(email, metadata.analyze(email));
                } catch (RuntimeException e) {
                    notes.add("Earlier message not stored as history: " + e.getMessage());
                }
            }
        }
        if (messages.stream().anyMatch(message -> !message.received())) {
            notes.add("Sent messages are not stored as history until approvals reach the agents");
        }
        BoardId board = wiring.coordinator.receive(selected.email(project));
        BlackboardSnapshot state = wiring.coordinator.run(board, architecture.limits());
        return response(state, notes);
    }

    private static Message message(JsonNode item) {
        if (item == null || !item.isObject()) {
            throw new Refused(400, "Each message must be a JSON object");
        }
        String direction = text(item, "direction", true);
        if (!direction.equals("received") && !direction.equals("sent")) {
            throw new Refused(400, "direction must be received or sent");
        }
        Instant date;
        try {
            date = Instant.parse(text(item, "date", true));
        } catch (DateTimeParseException e) {
            throw new Refused(400, "date must be an ISO-8601 instant");
        }
        return new Message(text(item, "id", true), text(item, "threadId", true), text(item, "sender", false),
                text(item, "recipient", false), text(item, "subject", false), text(item, "body", false), date,
                direction.equals("received"));
    }

    private static String text(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw new Refused(400, field + " is required");
            }
            return "";
        }
        if (!value.isTextual() || value.asText().length() > MAX_FIELD || (required && value.asText().isBlank())) {
            throw new Refused(400, field + " must be text of at most " + MAX_FIELD + " characters");
        }
        return value.asText();
    }

    private JsonNode response(BlackboardSnapshot state, List<String> notes) {
        ObjectNode out = JSON.createObjectNode();
        out.put("schemaVersion", "1");
        out.put("boardId", state.id().taskId());
        out.put("status", state.status().name());
        state.failure().ifPresent(failure -> out.putObject("failure").put("kind", failure.kind().name())
                .put("message", failure.message()));
        Optional<Draft> draft = state.drafts().isEmpty() ? Optional.empty()
                : Optional.of(state.drafts().get(state.drafts().size() - 1));
        if (draft.isPresent()) {
            ObjectNode node = out.putObject("draft").put("version", draft.get().version()).put("subject", draft.get().subject())
                    .put("body", draft.get().body()).put("agentId", draft.get().agentId())
                    .put("explanation", draft.get().explanation());
            // Review warnings (decisions or actions for the owner), also inside the explanation; for the reviewer.
            var warnings = node.putArray("warnings");
            for (String part : String.valueOf(draft.get().explanation()).split("; ")) {
                if (part.startsWith(org.supermas.llm.DecisionPhrases.WARNING_PREFIX)) {
                    warnings.add(part.substring(org.supermas.llm.DecisionPhrases.WARNING_PREFIX.length()));
                }
            }
        } else {
            out.putNull("draft");
        }
        state.routing().ifPresent(routing -> out.set("routing", routing(routing)));
        state.evidence().stream().map(ReplyPlan::from).flatMap(Optional::stream).reduce((first, second) -> second)
                .ifPresent(plan -> {
                    ObjectNode node = out.putObject("plan");
                    plan.requests().forEach(node.putArray("requests")::add);
                    plan.missing().forEach(node.putArray("missing")::add);
                });
        out.set("classification", classification(state, draft));
        ArrayNode noteList = out.putArray("notes");
        notes.forEach(noteList::add);
        return out;
    }

    private static JsonNode routing(RoutingDecision routing) {
        ObjectNode node = JSON.createObjectNode();
        node.put("match", routing.match().name());
        routing.selectedAgentId().ifPresentOrElse(id -> node.put("selectedAgentId", id), () -> node.putNull("selectedAgentId"));
        node.put("reason", routing.reason());
        ArrayNode candidates = node.putArray("candidates");
        for (AgentAssessment candidate : routing.candidates()) {
            candidates.addObject().put("agentId", candidate.agentId()).put("score", candidate.score())
                    .put("reason", candidate.reason());
        }
        return node;
    }

    /**
     * In the shape the mailbox's {@code normalizeClassification} reads. The labels say which specialists
     * <em>contributed</em> on the board, read from the board after the work, never from the routing decision
     * before it: one label per non-fallback role with a reply plan on the board, its display name from the role's
     * configuration file. Labels only flow from the board to the user, never back into control.
     *
     * <p>No specialist contribution (only the Generic fallback, or none) gives JSON null, so the mailbox keeps its
     * own labels: "no agent has anything to contribute" is not a class. Confidence is the contributor's routing
     * score when exactly one specialist contributed, and empty otherwise.
     */
    private JsonNode classification(BlackboardSnapshot state, Optional<Draft> draft) {
        // Blackboard: the specialists with a contribution; router baseline: the specialist with a reply plan.
        java.util.stream.Stream<String> authors = java.util.stream.Stream.concat(
                Contribution.latest(state.evidence()).stream().map(Contribution::agentId),
                state.evidence().stream().map(ReplyPlan::from).flatMap(Optional::stream).map(ReplyPlan::agentId));
        List<RoleDefinition> contributors = authors.distinct()
                .map(wiring.roles::role).flatMap(Optional::stream)
                .filter(role -> !role.fallback())
                .toList();
        if (contributors.isEmpty()) {
            return com.fasterxml.jackson.databind.node.NullNode.getInstance();
        }
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", "1");
        node.put("source", "supermas-agents");
        ArrayNode labels = node.putArray("labels");
        contributors.forEach(role -> labels.add(role.displayName()));
        ArrayNode ids = node.putArray("contributors");
        contributors.forEach(role -> ids.add(role.id()));
        Optional<Double> score = contributors.size() != 1 ? Optional.empty() : state.routing()
                .flatMap(routing -> routing.candidates().stream()
                        .filter(candidate -> candidate.agentId().equals(contributors.get(0).id()))
                        .map(AgentAssessment::score).findFirst());
        score.ifPresentOrElse(value -> node.put("confidence", Math.max(0, Math.min(1, value))), () -> node.putNull("confidence"));
        node.put("reason", "Contributed on the board: " + contributors.stream().map(RoleDefinition::id).toList());
        return node;
    }

    private static JsonNode error(String message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("error", message);
        return node;
    }
}
