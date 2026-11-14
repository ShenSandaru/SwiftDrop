package com.swiftdrop.assistant.application;

/**
 * The MCP routing headers of one HTTP request; each is {@code null} when absent.
 *
 * @param protocolVersion {@code MCP-Protocol-Version}
 * @param method          {@code Mcp-Method} (2026-07-28)
 * @param name            {@code Mcp-Name} (2026-07-28, {@code tools/call} only)
 */
public record McpHeaders(String protocolVersion, String method, String name) {
}
