package com.swiftdrop.assistant.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.assistant.application.McpHeaders;
import com.swiftdrop.assistant.application.McpResponse;
import com.swiftdrop.assistant.application.McpServer;

/**
 * The MCP endpoint (ADR 0010 §5): one JSON-RPC message per POST, answered with plain JSON. The
 * protocol rules live in {@link McpServer}; this class handles HTTP: the Origin check against DNS
 * rebinding, parsing, and status codes.
 */
@RestController
class McpController {

    static final String PATH = "/mcp";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final McpServer server;
    private final List<String> allowedOrigins;

    McpController(McpServer server, @Value("${swiftdrop.cors.allowed-origins}") List<String> allowedOrigins) {
        this.server = server;
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @PostMapping(PATH)
    ResponseEntity<Map<String, Object>> post(@RequestBody(required = false) String body,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @RequestHeader(name = "MCP-Protocol-Version", required = false) String protocolVersion,
            @RequestHeader(name = "Mcp-Method", required = false) String method,
            @RequestHeader(name = "Mcp-Name", required = false) String name,
            HttpServletRequest request) {
        if (origin != null && !allowedOrigins.contains(origin)) {
            return reply(new McpResponse(HttpStatus.FORBIDDEN.value(), Map.of("jsonrpc", "2.0",
                    "error", Map.of("code", -32600, "message", "Origin not allowed"))));
        }
        Object message;
        try {
            message = body == null || body.isBlank() ? null : JSON.readValue(body, Object.class);
        } catch (JacksonException e) {
            message = null;
        }
        if (message == null) {
            return reply(McpServer.parseError());
        }
        return reply(server.handle(message, new McpHeaders(protocolVersion, method, name), request.getRemoteAddr()));
    }

    /** No server-initiated stream and no sessions, so GET and DELETE are not allowed. */
    @RequestMapping(path = PATH, method = {RequestMethod.GET, RequestMethod.DELETE})
    ResponseEntity<Void> notAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(HttpMethod.POST).build();
    }

    private static ResponseEntity<Map<String, Object>> reply(McpResponse response) {
        if (response.body() == null) {
            return ResponseEntity.status(response.status()).build();
        }
        return ResponseEntity.status(response.status()).contentType(MediaType.APPLICATION_JSON).body(response.body());
    }
}
