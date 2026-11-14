package com.swiftdrop.assistant.application;

import java.util.Map;

/**
 * What to send back for one MCP message.
 *
 * @param status HTTP status
 * @param body   the JSON-RPC response, or {@code null} for an accepted notification
 */
public record McpResponse(int status, Map<String, Object> body) {
}
