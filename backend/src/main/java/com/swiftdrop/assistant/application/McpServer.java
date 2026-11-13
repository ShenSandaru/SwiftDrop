package com.swiftdrop.assistant.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

/**
 * The tool part of the Model Context Protocol, hand-written (ADR 0010 §5). It serves the stateless
 * 2026-07-28 revision ({@code server/discover}, headers checked against the body) and, for clients
 * that open with {@code initialize}, the 2025 revisions without sessions. It exposes the same
 * read-only tools as the chat. Logs carry the method and tool name, never arguments.
 */
@Service
public class McpServer {

    public static final String MODERN_VERSION = "2026-07-28";
    static final List<String> LEGACY_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26");
    static final List<String> SUPPORTED_VERSIONS = List.of(MODERN_VERSION, "2025-11-25", "2025-06-18", "2025-03-26");

    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int HEADER_MISMATCH = -32020;
    static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    private static final String META_VERSION = "io.modelcontextprotocol/protocolVersion";
    private static final Map<String, Object> SERVER_INFO = Map.of("name", "swiftdrop", "version", "1.0.0");
    private static final String INSTRUCTIONS = "SwiftDrop parcel delivery in Sri Lanka: track a parcel by its "
            + "tracking number, estimate a delivery fee between two cities, and list the service areas. "
            + "Read-only and public data only.";
    private static final long DISCOVERY_TTL_MS = 3_600_000;
    private static final long TOOLS_TTL_MS = 300_000;
    private static final Pattern BASE64_NAME = Pattern.compile("^=\\?base64\\?([A-Za-z0-9+/=]*)\\?=$");

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, AssistantTool> tools;
    private final ClientRateLimiter rateLimiter;

    McpServer(List<AssistantTool> tools, ClientRateLimiter rateLimiter) {
        this.tools = tools.stream().collect(Collectors.toMap(tool -> tool.spec().name(), Function.identity()));
        this.rateLimiter = rateLimiter;
    }

    /** A request whose body is not valid JSON. */
    public static McpResponse parseError() {
        return error(400, null, PARSE_ERROR, "Parse error: invalid JSON", null);
    }

    /**
     * Handles one parsed JSON-RPC message.
     *
     * @param client identifies the caller for the rate limit (the remote address)
     */
    @SuppressWarnings("unchecked")
    public McpResponse handle(Object parsed, McpHeaders headers, String client) {
        if (!(parsed instanceof Map<?, ?> raw)) {
            return error(400, null, INVALID_REQUEST, "Send one JSON-RPC message per request; batches are not supported", null);
        }
        Map<String, Object> message = (Map<String, Object>) raw;
        boolean notification = !message.containsKey("id");
        Object id = message.get("id");
        if (!"2.0".equals(message.get("jsonrpc")) || !(message.get("method") instanceof String method) || method.isBlank()) {
            return error(400, notification ? null : id, INVALID_REQUEST, "Invalid JSON-RPC request", null);
        }
        if (notification) {
            return new McpResponse(202, null);
        }
        Object rawParams = message.get("params");
        if (rawParams != null && !(rawParams instanceof Map)) {
            return error(200, id, INVALID_PARAMS, "params must be an object", null);
        }
        Map<String, Object> params = rawParams == null ? Map.of() : (Map<String, Object>) rawParams;
        log.info("MCP {}", method);

        if ("initialize".equals(method)) {
            return ok(id, initialize(params));
        }
        Optional<String> version = metaVersion(params);
        return version.isPresent()
                ? modern(id, method, params, version.get(), headers, client)
                : legacy(id, method, params, headers, client);
    }

    // --- 2026-07-28: stateless, headers must match the body ---------------------------------

    private McpResponse modern(Object id, String method, Map<String, Object> params, String version,
            McpHeaders headers, String client) {
        if (!MODERN_VERSION.equals(version)) {
            return unsupportedVersion(id, version);
        }
        if (!version.equals(headers.protocolVersion())) {
            return error(400, id, HEADER_MISMATCH, "Header mismatch: MCP-Protocol-Version must equal the body's protocol version", null);
        }
        if (!method.equals(headers.method())) {
            return error(400, id, HEADER_MISMATCH, "Header mismatch: Mcp-Method must equal the request method", null);
        }
        if ("tools/call".equals(method) && !(params.get("name") instanceof String name && name.equals(decodeName(headers.name())))) {
            return error(400, id, HEADER_MISMATCH, "Header mismatch: Mcp-Name must equal the tool name", null);
        }
        return switch (method) {
            case "server/discover" -> ok(id, complete(discover()));
            case "ping" -> ok(id, complete(new LinkedHashMap<>()));
            case "tools/list" -> {
                Map<String, Object> result = complete(toolList());
                result.put("ttlMs", TOOLS_TTL_MS);
                result.put("cacheScope", "public");
                yield ok(id, result);
            }
            case "tools/call" -> callTool(id, params, client, true);
            default -> error(404, id, METHOD_NOT_FOUND, "Method not found", null);
        };
    }

    private Map<String, Object> discover() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supportedVersions", SUPPORTED_VERSIONS);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("instructions", INSTRUCTIONS);
        result.put("ttlMs", DISCOVERY_TTL_MS);
        result.put("cacheScope", "public");
        result.put("_meta", Map.of("io.modelcontextprotocol/serverInfo", SERVER_INFO));
        return result;
    }

    // --- 2025 revisions: initialize handshake, no sessions -----------------------------------

    private McpResponse legacy(Object id, String method, Map<String, Object> params, McpHeaders headers, String client) {
        if (headers.protocolVersion() != null && !LEGACY_VERSIONS.contains(headers.protocolVersion())) {
            return unsupportedVersion(id, headers.protocolVersion());
        }
        return switch (method) {
            case "ping" -> ok(id, new LinkedHashMap<>());
            case "tools/list" -> ok(id, toolList());
            case "tools/call" -> callTool(id, params, client, false);
            default -> error(200, id, METHOD_NOT_FOUND, "Method not found", null);
        };
    }

    private static Map<String, Object> initialize(Map<String, Object> params) {
        // Answer with the client's version when we know it, otherwise our newest 2025 version
        Object requested = params.get("protocolVersion");
        String version = requested instanceof String text && LEGACY_VERSIONS.contains(text) ? text : LEGACY_VERSIONS.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", version);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", SERVER_INFO);
        result.put("instructions", INSTRUCTIONS);
        return result;
    }

    // --- tools -------------------------------------------------------------------------------

    private Map<String, Object> toolList() {
        List<Map<String, Object>> list = tools.values().stream()
                .map(AssistantTool::spec)
                .sorted(Comparator.comparing(ToolSpec::name))
                .map(spec -> {
                    Map<String, Object> tool = new LinkedHashMap<>();
                    tool.put("name", spec.name());
                    tool.put("description", spec.description());
                    tool.put("inputSchema", spec.parameters());
                    return tool;
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", list);
        return result;
    }

    @SuppressWarnings("unchecked")
    private McpResponse callTool(Object id, Map<String, Object> params, String client, boolean modern) {
        AssistantTool tool = params.get("name") instanceof String name ? tools.get(name) : null;
        if (tool == null) {
            log.warn("MCP call for an unknown tool");
            return error(200, id, INVALID_PARAMS, "Unknown tool", null);
        }
        Object arguments = params.get("arguments");
        if (arguments != null && !(arguments instanceof Map)) {
            return error(200, id, INVALID_PARAMS, "arguments must be an object", null);
        }
        String toolName = tool.spec().name();
        if (!rateLimiter.tryAcquire("mcp:" + client)) {
            log.warn("MCP tool {} refused by the rate limit", toolName);
            return ok(id, toolResult(Map.of("error", "Too many tool calls. Please wait a minute."), true, modern));
        }
        Map<String, Object> result;
        try {
            result = tool.execute(arguments == null ? Map.of() : (Map<String, Object>) arguments);
        } catch (RuntimeException e) {
            log.warn("MCP tool {} failed: {}", toolName, e.getClass().getSimpleName());
            return ok(id, toolResult(Map.of("error", "The tool failed. Try again later."), true, modern));
        }
        boolean failed = result.containsKey("error");
        log.info("MCP tool {} {}", toolName, failed ? "refused the input" : "answered");
        return ok(id, toolResult(result, failed, modern));
    }

    /** Structured result plus the same JSON as text, for clients that read only text. */
    private static Map<String, Object> toolResult(Map<String, Object> result, boolean isError, boolean modern) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (modern) {
            body.put("resultType", "complete");
        }
        body.put("content", List.of(Map.of("type", "text", "text", JSON.writeValueAsString(result))));
        body.put("structuredContent", result);
        body.put("isError", isError);
        return body;
    }

    // --- helpers -----------------------------------------------------------------------------

    private static Optional<String> metaVersion(Map<String, Object> params) {
        return params.get("_meta") instanceof Map<?, ?> meta && meta.get(META_VERSION) instanceof String version
                ? Optional.of(version)
                : Optional.empty();
    }

    /** {@code Mcp-Name} may be sent as {@code =?base64?...?=} when the name is not plain ASCII. */
    static String decodeName(String header) {
        if (header == null) {
            return null;
        }
        Matcher matcher = BASE64_NAME.matcher(header);
        if (!matcher.matches()) {
            return header;
        }
        try {
            return new String(Base64.getDecoder().decode(matcher.group(1)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, Object> complete(Map<String, Object> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resultType", "complete");
        body.putAll(result);
        return body;
    }

    private static McpResponse ok(Object id, Map<String, Object> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return new McpResponse(200, body);
    }

    private static McpResponse unsupportedVersion(Object id, String requested) {
        return error(400, id, UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version",
                Map.of("supported", SUPPORTED_VERSIONS, "requested", requested));
    }

    private static McpResponse error(int status, Object id, int code, String message, Map<String, Object> data) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", error);
        return new McpResponse(status, body);
    }
}
