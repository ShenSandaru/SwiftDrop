package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpServerTest {

    private static final String MODERN = McpServer.MODERN_VERSION;
    private static final McpHeaders NO_HEADERS = new McpHeaders(null, null, null);

    private ClientRateLimiter rateLimiter;
    private AtomicInteger trackRuns;
    private McpServer server;

    private static AssistantTool tool(String name, AtomicInteger runs, Map<String, Object> result) {
        return new AssistantTool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(name, "Tool " + name, Map.of("type", "object", "properties", Map.of()));
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments) {
                runs.incrementAndGet();
                return result;
            }
        };
    }

    @BeforeEach
    void setUp() {
        rateLimiter = mock(ClientRateLimiter.class);
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        trackRuns = new AtomicInteger();
        AssistantTool failing = new AssistantTool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("broken", "fails", Map.of("type", "object"));
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments) {
                throw new IllegalStateException("jdbc:postgresql://secret-host");
            }
        };
        server = new McpServer(List.of(
                tool("track_parcel", trackRuns, Map.of("found", true, "status", "AT_HUB")),
                tool("estimate_fee", new AtomicInteger(), Map.of("error", "SwiftDrop does not deliver to or from that city.")),
                failing), rateLimiter);
    }

    private static Map<String, Object> request(Object id, String method, Map<String, Object> params) {
        Map<String, Object> message = new HashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("method", method);
        if (params != null) {
            message.put("params", params);
        }
        return message;
    }

    private static Map<String, Object> modernParams(Map<String, Object> extra) {
        Map<String, Object> params = new HashMap<>(extra);
        params.put("_meta", Map.of("io.modelcontextprotocol/protocolVersion", MODERN,
                "io.modelcontextprotocol/clientCapabilities", Map.of()));
        return params;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(McpResponse response) {
        return (Map<String, Object>) response.body().get("result");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> error(McpResponse response) {
        return (Map<String, Object>) response.body().get("error");
    }

    // --- legacy (2025) -------------------------------------------------------------------------

    @Test
    void legacyInitializeAnswersWithTheClientsVersionAndToolCapability() {
        McpResponse response = server.handle(request(1, "initialize",
                Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of())), NO_HEADERS, "c");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).containsEntry("jsonrpc", "2.0").containsEntry("id", 1);
        assertThat(result(response)).containsEntry("protocolVersion", "2025-06-18")
                .containsEntry("capabilities", Map.of("tools", Map.of()))
                .containsKeys("serverInfo", "instructions");
    }

    @Test
    void legacyInitializeOffersItsNewestVersionForAnUnknownOne() {
        McpResponse response = server.handle(request(1, "initialize", Map.of("protocolVersion", "2024-01-01")),
                NO_HEADERS, "c");

        assertThat(result(response)).containsEntry("protocolVersion", "2025-11-25");
    }

    @Test
    void notificationsAreAcceptedWithoutABody() {
        Map<String, Object> notification = Map.of("jsonrpc", "2.0", "method", "notifications/initialized");

        McpResponse response = server.handle(notification, NO_HEADERS, "c");

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.body()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyToolsListHasTheToolsSortedByNameWithTheirSchemas() {
        McpResponse response = server.handle(request(2, "tools/list", null),
                new McpHeaders("2025-11-25", null, null), "c");

        List<Map<String, Object>> tools = (List<Map<String, Object>>) result(response).get("tools");
        assertThat(tools).extracting(tool -> tool.get("name")).containsExactly("broken", "estimate_fee", "track_parcel");
        assertThat(tools.get(2)).containsKeys("description", "inputSchema");
        assertThat(result(response)).doesNotContainKey("resultType");
    }

    @Test
    void legacyRequestWithAnUnsupportedVersionHeaderIsRejected() {
        McpResponse response = server.handle(request(2, "tools/list", null), new McpHeaders("1999-01-01", null, null), "c");

        assertThat(response.status()).isEqualTo(400);
        assertThat(error(response)).containsEntry("code", McpServer.UNSUPPORTED_PROTOCOL_VERSION);
    }

    // --- modern (2026-07-28) -------------------------------------------------------------------

    @Test
    void discoverDescribesVersionsCapabilitiesAndServer() {
        McpResponse response = server.handle(request("d1", "server/discover", modernParams(Map.of())),
                new McpHeaders(MODERN, "server/discover", null), "c");

        assertThat(response.status()).isEqualTo(200);
        assertThat(result(response)).containsEntry("resultType", "complete")
                .containsEntry("capabilities", Map.of("tools", Map.of()))
                .containsEntry("cacheScope", "public")
                .containsKeys("supportedVersions", "ttlMs", "_meta");
        assertThat(result(response).get("supportedVersions")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .contains(MODERN, "2025-11-25");
    }

    @Test
    void modernToolsListCarriesCachingFields() {
        McpResponse response = server.handle(request(3, "tools/list", modernParams(Map.of())),
                new McpHeaders(MODERN, "tools/list", null), "c");

        assertThat(result(response)).containsEntry("resultType", "complete").containsKeys("tools", "ttlMs", "cacheScope");
    }

    @Test
    void modernRequestsMustRepeatVersionAndMethodInTheHeaders() {
        Map<String, Object> list = request(3, "tools/list", modernParams(Map.of()));

        for (McpHeaders wrong : List.of(new McpHeaders(null, "tools/list", null),
                new McpHeaders("2025-11-25", "tools/list", null),
                new McpHeaders(MODERN, null, null),
                new McpHeaders(MODERN, "tools/call", null))) {
            McpResponse response = server.handle(list, wrong, "c");
            assertThat(response.status()).as("%s", wrong).isEqualTo(400);
            assertThat(error(response)).containsEntry("code", McpServer.HEADER_MISMATCH);
        }
    }

    @Test
    void modernToolCallNeedsTheToolNameInTheHeaderPlainOrBase64() {
        Map<String, Object> call = request(4, "tools/call", modernParams(Map.of("name", "track_parcel",
                "arguments", Map.of("trackingNumber", "SD1"))));
        String encoded = "=?base64?" + Base64.getEncoder().encodeToString("track_parcel".getBytes(StandardCharsets.UTF_8)) + "?=";

        assertThat(server.handle(call, new McpHeaders(MODERN, "tools/call", "estimate_fee"), "c").status()).isEqualTo(400);
        assertThat(server.handle(call, new McpHeaders(MODERN, "tools/call", null), "c").status()).isEqualTo(400);
        assertThat(server.handle(call, new McpHeaders(MODERN, "tools/call", "track_parcel"), "c").status()).isEqualTo(200);
        assertThat(server.handle(call, new McpHeaders(MODERN, "tools/call", encoded), "c").status()).isEqualTo(200);
    }

    @Test
    void anUnsupportedModernVersionListsTheSupportedOnes() {
        Map<String, Object> params = Map.of("_meta", Map.of("io.modelcontextprotocol/protocolVersion", "2030-01-01"));

        McpResponse response = server.handle(request(5, "tools/list", params), new McpHeaders("2030-01-01", "tools/list", null), "c");

        assertThat(response.status()).isEqualTo(400);
        assertThat(error(response)).containsEntry("code", McpServer.UNSUPPORTED_PROTOCOL_VERSION).containsKey("data");
    }

    @Test
    void anUnknownModernMethodIsNotFound() {
        McpResponse response = server.handle(request(6, "resources/list", modernParams(Map.of())),
                new McpHeaders(MODERN, "resources/list", null), "c");

        assertThat(response.status()).isEqualTo(404);
        assertThat(error(response)).containsEntry("code", McpServer.METHOD_NOT_FOUND);
    }

    // --- tools/call ----------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void toolCallReturnsStructuredResultAndTheSameJsonAsText() {
        McpResponse response = server.handle(request(7, "tools/call", modernParams(Map.of("name", "track_parcel",
                "arguments", Map.of("trackingNumber", "SD1")))), new McpHeaders(MODERN, "tools/call", "track_parcel"), "1.2.3.4");

        Map<String, Object> result = result(response);
        assertThat(result).containsEntry("resultType", "complete").containsEntry("isError", false)
                .containsEntry("structuredContent", Map.of("found", true, "status", "AT_HUB"));
        Map<String, Object> text = ((List<Map<String, Object>>) result.get("content")).get(0);
        assertThat(text).containsEntry("type", "text");
        assertThat((String) text.get("text")).contains("\"status\":\"AT_HUB\"");
        verify(rateLimiter).tryAcquire("mcp:1.2.3.4");
    }

    @Test
    void aToolThatRefusesTheInputAnswersWithIsError() {
        McpResponse response = server.handle(request(8, "tools/call", Map.of("name", "estimate_fee", "arguments", Map.of())),
                NO_HEADERS, "c");

        assertThat(result(response)).containsEntry("isError", true).doesNotContainKey("resultType");
    }

    @Test
    void aFailingToolAnswersSafelyWithoutLeakingTheException() {
        McpResponse response = server.handle(request(9, "tools/call", Map.of("name", "broken")), NO_HEADERS, "c");

        assertThat(result(response)).containsEntry("isError", true);
        assertThat(response.body().toString()).doesNotContain("secret-host");
    }

    @Test
    void unknownToolsAndBadArgumentsAreInvalidParams() {
        McpResponse unknown = server.handle(request(10, "tools/call", Map.of("name", "delete_everything")), NO_HEADERS, "c");
        McpResponse badArgs = server.handle(request(11, "tools/call", Map.of("name", "track_parcel", "arguments", "x")),
                NO_HEADERS, "c");

        assertThat(error(unknown)).containsEntry("code", McpServer.INVALID_PARAMS);
        assertThat(error(badArgs)).containsEntry("code", McpServer.INVALID_PARAMS);
        assertThat(trackRuns).hasValue(0);
    }

    @Test
    void theRateLimitStopsToolCallsWithoutRunningTheTool() {
        when(rateLimiter.tryAcquire("mcp:c")).thenReturn(false);

        McpResponse response = server.handle(request(12, "tools/call", Map.of("name", "track_parcel")), NO_HEADERS, "c");

        assertThat(result(response)).containsEntry("isError", true);
        assertThat(trackRuns).hasValue(0);
    }

    // --- malformed messages --------------------------------------------------------------------

    @Test
    void batchesAndMalformedMessagesAreInvalidRequests() {
        McpResponse batch = server.handle(List.of(request(1, "ping", null)), NO_HEADERS, "c");
        McpResponse noVersion = server.handle(Map.of("id", 1, "method", "ping"), NO_HEADERS, "c");
        McpResponse noMethod = server.handle(Map.of("jsonrpc", "2.0", "id", 1), NO_HEADERS, "c");

        for (McpResponse response : List.of(batch, noVersion, noMethod)) {
            assertThat(response.status()).isEqualTo(400);
            assertThat(error(response)).containsEntry("code", McpServer.INVALID_REQUEST);
        }
    }

    @Test
    void nonObjectParamsAreInvalidParams() {
        Map<String, Object> message = new HashMap<>(request(1, "tools/list", null));
        message.put("params", List.of());

        assertThat(error(server.handle(message, NO_HEADERS, "c"))).containsEntry("code", McpServer.INVALID_PARAMS);
    }

    @Test
    void decodesOnlyWellFormedBase64Names() {
        assertThat(McpServer.decodeName("track_parcel")).isEqualTo("track_parcel");
        assertThat(McpServer.decodeName("=?base64?dHJhY2tfcGFyY2Vs?=")).isEqualTo("track_parcel");
        assertThat(McpServer.decodeName(null)).isNull();
    }

    @Test
    void parseErrorHasNoId() {
        McpResponse response = McpServer.parseError();

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).containsEntry("id", null);
        assertThat(error(response)).containsEntry("code", McpServer.PARSE_ERROR);
    }
}
