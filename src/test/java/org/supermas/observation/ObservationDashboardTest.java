package org.supermas.observation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Static deployment contract checks; these do not claim Grafana rendered the configuration. */
class ObservationDashboardTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void everyPanelUsesAuthenticatedBackendQueriesAndTheBoundedBoardApi() throws Exception {
        JsonNode dashboard = json.readTree(Files.readString(Path.of("observability/grafana/dashboards/blackboard.json")));
        Set<String> types = new HashSet<>();
        for (JsonNode panel : dashboard.path("panels")) {
            types.add(panel.path("type").asText());
            for (JsonNode query : panel.path("targets")) {
                assertEquals("supermas-observation", query.path("datasource").path("uid").asText());
                assertEquals("backend", query.path("parser").asText());
                assertEquals("GET", query.path("url_options").path("method").asText());
                String url = query.path("url").asText();
                if (!url.equals("http://127.0.0.1:8091/boards")) {
                    assertTrue(url.startsWith("http://127.0.0.1:8091/view?board=${board:percentencode}"));
                    assertTrue(url.contains("agent=${expert:percentencode}"));
                    assertTrue(url.contains("from=${__from}&to=${__to}"));
                }
                assertFalse(query.has("data"));
            }
        }
        assertTrue(types.containsAll(Set.of("table", "stat", "state-timeline", "nodeGraph")));
        for (JsonNode variable : dashboard.path("templating").path("list")) {
            assertEquals("infinity", variable.path("query").path("queryType").asText());
            assertTrue(variable.path("query").has("infinityQuery"));
            assertFalse(variable.path("multi").asBoolean());
        }
        assertEquals("15s", dashboard.path("refresh").asText());
        for (JsonNode panel : dashboard.path("panels")) {
            if (panel.path("type").asText().equals("state-timeline")) {
                JsonNode order = panel.path("transformations").get(0).path("options").path("indexByName");
                assertEquals(0, order.path("start").asInt(-1));
                assertEquals(1, order.path("end").asInt(-1));
                assertEquals("${__field.labels.agent}", panel.path("fieldConfig").path("defaults").path("displayName").asText());
            }
            if (panel.path("type").asText().equals("nodeGraph")) {
                for (JsonNode target : panel.path("targets")) {
                    for (JsonNode column : target.path("columns")) {
                        if (column.path("text").asText().equals("detail__sourceRevision"))
                            assertEquals("string", column.path("type").asText(), "Revision metadata must not become an inferred numeric graph statistic");
                    }
                }
            }
        }
        String serialized = dashboard.toString().toLowerCase(Locale.ROOT);
        assertFalse(serialized.contains("bearer "));
        assertFalse(serialized.contains("password"));
        assertFalse(serialized.contains("jdbc:"));
        assertFalse(serialized.contains("sqlite"));
        JsonNode versions = json.readTree(Files.readString(Path.of("observability/grafana/versions.json")));
        assertTrue(versions.path("grafana").asText().matches("\\d+\\.\\d+\\.\\d+"));
        assertTrue(versions.path("infinity").asText().matches("\\d+\\.\\d+\\.\\d+"));
        String datasource = Files.readString(Path.of("observability/grafana/provisioning/datasources/supermas.yaml"));
        assertTrue(datasource.contains("bearerToken: $SUPERMAS_OBSERVATION_TOKEN"));
        assertTrue(datasource.contains("auth_method: bearerToken"));
        assertFalse(datasource.contains("ignoreStatusCodeCheck: true"));
        assertFalse(datasource.contains("tlsSkipVerify: true"));
    }
}
