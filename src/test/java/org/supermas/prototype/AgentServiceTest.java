package org.supermas.prototype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The loopback endpoint the desktop mailbox calls: its refusals, then a whole reply. */
class AgentServiceTest {
    private static final String TOKEN = "t".repeat(40);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STUDENT = """
            {"id": "m1", "threadId": "th1", "sender": "Sarah Klein <sarah@students.example.edu>",
             "recipient": "prof@example.edu", "subject": "Extension for CS204", "direction": "received",
             "date": "2026-11-05T09:00:00Z",
             "body": "Dear Prof. Martin, I am a student. Could I have an extension for the CS204 assignment? My student number is 20231187."}""";

    @TempDir
    Path data;

    private AgentService service;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        service = new AgentService(new PrototypeApp.Options(null, Path.of("src/test/resources/university/knowledge"),
                data, "uni"), 0, TOKEN);
        service.start();
    }

    @AfterEach
    void stop() {
        service.close();
    }

    private HttpResponse<String> post(String body, String token) throws Exception {
        return post("/v1/reply", body, token);
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://" + service.host() + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header(AgentService.TOKEN_HEADER, token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String request(String messageId, String... messages) {
        return "{\"messageId\": \"" + messageId + "\", \"messages\": [" + String.join(",", messages) + "]}";
    }

    @Test
    void aStudentEmailComesBackAsADraftWithItsRoleAsLabel() throws Exception {
        HttpResponse<String> response = post(request("m1", STUDENT), TOKEN);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("AWAITING_REVIEW", body.get("status").asText());
        // The blackboard: the student contributed, the composer wrote the one draft.
        assertEquals("composer", body.get("draft").get("agentId").asText());
        assertTrue(body.get("draft").get("body").asText().contains("extension"), body.toString());
        assertEquals("CLEAR", body.get("routing").get("match").asText());
        JsonNode classification = body.get("classification");
        assertEquals("Student requests", classification.get("labels").get(0).asText());
        assertTrue(classification.get("confidence").asDouble() > 0 && classification.get("confidence").asDouble() <= 1);
        assertEquals("supermas-agents", classification.get("source").asText());
        // Read from the board after the work: the specialists that contributed a reply plan.
        assertEquals("[\"student\"]", classification.get("contributors").toString());
    }

    @Test
    void noFittingRoleGivesADraftButNoClassification() throws Exception {
        String lunch = STUDENT.replace("Extension for CS204", "Lunch").replaceAll("\"body\": \"[^\"]*\"",
                "\"body\": \"Would you like to have lunch on Thursday?\"");

        JsonNode body = JSON.readTree(post(request("m1", lunch), TOKEN).body());

        assertEquals("NONE", body.get("routing").get("match").asText(), body.toString());
        assertEquals("composer", body.get("draft").get("agentId").asText());
        assertTrue(body.get("draft").get("explanation").asText().contains("generic"), body.toString());
        // Only the fallback contributed: the mailbox keeps its own labels.
        assertTrue(body.get("classification").isNull(), body.toString());
    }

    @Test
    void earlierReceivedMailBecomesHistoryAndSentMailIsOnlyNoted() throws Exception {
        String earlier = STUDENT.replace("\"m1\"", "\"m0\"").replace("2026-11-05T09:00:00Z", "2026-11-04T09:00:00Z");
        String sent = """
                {"id": "s1", "threadId": "th1", "sender": "You", "recipient": "sarah@students.example.edu",
                 "subject": "Re: Extension", "direction": "sent", "date": "2026-11-04T12:00:00Z", "body": "Which course?"}""";

        JsonNode body = JSON.readTree(post(request("m1", earlier, sent, STUDENT), TOKEN).body());

        assertEquals("AWAITING_REVIEW", body.get("status").asText());
        assertEquals(1, body.get("notes").size(), body.toString());
        assertTrue(body.get("notes").get(0).asText().startsWith("Sent messages are not stored"));
        // Asking again is harmless: the identical history record is ignored.
        assertEquals(200, post(request("m1", earlier, STUDENT), TOKEN).statusCode());
    }

    @Test
    void replyNeedIsJudgedWithoutAModelAndAlsoComesWithEveryDraft() throws Exception {
        HttpResponse<String> asks = post(AgentService.REPLY_NEED_PATH, request("m1", STUDENT), TOKEN);
        assertEquals(200, asks.statusCode(), asks.body());
        assertEquals("NEEDED", JSON.readTree(asks.body()).get("replyNeed").get("verdict").asText());
        String thanks = STUDENT.replaceAll("\"body\": \"[^\"]*\"", "\"body\": \"Thank you very much, that helps.\"");
        JsonNode judged = JSON.readTree(post(AgentService.REPLY_NEED_PATH, request("m1", thanks), TOKEN).body());
        assertEquals("NOT_NEEDED", judged.get("replyNeed").get("verdict").asText());
        assertEquals("only thanks or an acknowledgement", judged.get("replyNeed").get("reasons").get(0).asText());
        assertTrue(judged.get("draft") == null, "no board, no draft");
        // A person asked for a draft: the service still writes one, and says that no reply seemed needed.
        JsonNode drafted = JSON.readTree(post(request("m1", thanks), TOKEN).body());
        assertEquals("AWAITING_REVIEW", drafted.get("status").asText(), drafted.toString());
        assertEquals("NOT_NEEDED", drafted.get("replyNeed").get("verdict").asText());
        assertEquals(401, post(AgentService.REPLY_NEED_PATH, request("m1", STUDENT), null).statusCode());
    }

    @Test
    void aMissingOrWrongTokenIsRefusedBeforeAnythingIsRead() throws Exception {
        assertEquals(401, post(request("m1", STUDENT), null).statusCode());
        assertEquals(401, post(request("m1", STUDENT), "x".repeat(40)).statusCode());
    }

    @Test
    void anotherHostNameIsRefused() throws Exception {
        String port = service.host().substring(service.host().indexOf(':') + 1);
        try (Socket socket = new Socket("127.0.0.1", Integer.parseInt(port))) {
            OutputStream out = socket.getOutputStream();
            out.write(("POST /v1/reply HTTP/1.1\r\nHost: evil.example:" + port + "\r\n" + AgentService.TOKEN_HEADER + ": "
                    + TOKEN + "\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String status = new String(in.readNBytes(12), StandardCharsets.US_ASCII);
            assertEquals("HTTP/1.1 403", status);
        }
    }

    @Test
    void malformedConversationsAreRefusedWithAReason() throws Exception {
        String sent = STUDENT.replace("\"received\"", "\"sent\"");
        String otherThread = STUDENT.replace("\"m1\"", "\"m2\"").replace("\"th1\"", "\"th2\"");
        assertEquals(400, post(request("m1", sent), TOKEN).statusCode(), "only received mail is answered");
        assertEquals(400, post(request("m1", STUDENT, otherThread), TOKEN).statusCode(), "one thread only");
        assertEquals(400, post(request("absent", STUDENT), TOKEN).statusCode());
        assertEquals(400, post("not json", TOKEN).statusCode());
        assertEquals(400, post(request("m1", STUDENT.replace("2026-11-05T09:00:00Z", "yesterday")), TOKEN).statusCode());
        HttpResponse<String> tooBig = post("{\"x\": \"" + "a".repeat(AgentService.MAX_BODY) + "\"}", TOKEN);
        assertEquals(413, tooBig.statusCode());
        HttpResponse<String> get = client.send(HttpRequest.newBuilder(URI.create("http://" + service.host() + "/v1/reply"))
                .header(AgentService.TOKEN_HEADER, TOKEN).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, get.statusCode());
    }

    @Test
    void theOnlineModelNeedsASecondOptInForTheMailboxAndNothingIsRecorded() {
        java.util.Map<String, String> online = java.util.Map.of(org.supermas.llm.LlmSettings.API_KEY, "sk-test-key");
        java.util.Map<String, String> flags = java.util.Map.of("--llm-provider", "online", "--llm-model", "m");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> AgentService.checkModel(
                org.supermas.llm.LlmSettings.from(online, flags)));
        assertTrue(refused.getMessage().contains(org.supermas.llm.LlmSettings.ONLINE_MAIL), refused.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new AgentService(new PrototypeApp.Options(null,
                Path.of("src/test/resources/university/knowledge"), data.resolve("online"), "uni",
                PrototypeApp.Architecture.BLACKBOARD, 0.4, org.supermas.llm.LlmSettings.from(online, flags)), 0, TOKEN));

        java.util.Map<String, String> consented = new java.util.HashMap<>(online);
        consented.put(org.supermas.llm.LlmSettings.ONLINE_MAIL, "yes");
        AgentService.checkModel(org.supermas.llm.LlmSettings.from(consented, flags));

        java.util.Map<String, String> recording = java.util.Map.of("--llm-endpoint", "http://127.0.0.1:11434",
                "--llm-model", "m", "--llm-cache", data.resolve("cache").toString());
        assertThrows(IllegalArgumentException.class, () -> AgentService.checkModel(
                org.supermas.llm.LlmSettings.from(java.util.Map.of(), recording)));
    }

    @Test
    void aShortTokenStopsTheServiceFromStarting() {
        assertThrows(IllegalArgumentException.class, () -> new AgentService(new PrototypeApp.Options(null,
                Path.of("src/test/resources/university/knowledge"), data.resolve("other"), "uni"), 0, "short"));
    }
}
