package com.swiftdrop.assistant.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.swiftdrop.assistant.application.AssistantProperties;
import com.swiftdrop.assistant.application.ChatRole;
import com.swiftdrop.assistant.application.LlmClient;
import com.swiftdrop.assistant.application.LlmMessage;
import com.swiftdrop.assistant.application.LlmReply;
import com.swiftdrop.assistant.application.LlmUnavailableException;
import com.swiftdrop.assistant.application.ToolSpec;

/**
 * Google Gemini through its REST {@code generateContent} endpoint with function calling (ADR 0010 §3).
 * The key travels in the {@code x-goog-api-key} header, never in the URL. Failure messages carry the
 * status or exception type only: no response body, no prompt text, no key.
 */
class GeminiLlmClient implements LlmClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() { };
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final RestClient restClient;
    private final AssistantProperties properties;

    GeminiLlmClient(RestClient restClient, AssistantProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public LlmReply generate(String systemPrompt, List<LlmMessage> conversation, List<ToolSpec> tools) {
        if (!properties.hasApiKey()) {
            throw new LlmUnavailableException("No Gemini API key is configured");
        }
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(systemPrompt, conversation, tools))
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            // The model name is not a secret, and it shows at once when the setting was not picked up
            String hint = status.value() == 404 ? " (is SWIFTDROP_ASSISTANT_MODEL a model your key can use?)" : "";
            throw new LlmUnavailableException("Gemini answered HTTP " + status.value()
                    + " for model " + properties.model() + hint, e);
        } catch (RestClientException e) {
            throw new LlmUnavailableException("Gemini request failed: " + e.getClass().getSimpleName(), e);
        }
        return reply(response);
    }

    // --- request -------------------------------------------------------------------------------

    private static Map<String, Object> request(String systemPrompt, List<LlmMessage> conversation,
            List<ToolSpec> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", contents(conversation));
        if (!tools.isEmpty()) {
            body.put("tools", List.of(Map.of("functionDeclarations", tools.stream().map(GeminiLlmClient::declaration).toList())));
        }
        body.put("generationConfig", Map.of("maxOutputTokens", MAX_OUTPUT_TOKENS, "temperature", 0.2));
        return body;
    }

    private static Map<String, Object> declaration(ToolSpec tool) {
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("name", tool.name());
        declaration.put("description", tool.description());
        // A tool without arguments must leave "parameters" out: Gemini rejects an empty object schema
        Object properties = tool.parameters().get("properties");
        if (properties instanceof Map<?, ?> map && !map.isEmpty()) {
            declaration.put("parameters", tool.parameters());
        }
        return declaration;
    }

    /** Consecutive tool calls become one model turn and consecutive results one user turn, as Gemini expects. */
    private static List<Map<String, Object>> contents(List<LlmMessage> conversation) {
        List<Map<String, Object>> contents = new ArrayList<>();
        Class<?> openGroup = null;
        List<Map<String, Object>> openParts = null;
        for (LlmMessage message : conversation) {
            boolean groupable = !(message instanceof LlmMessage.Text);
            if (groupable && message.getClass().equals(openGroup)) {
                openParts.add(part(message));
                continue;
            }
            openParts = new ArrayList<>();
            openParts.add(part(message));
            openGroup = groupable ? message.getClass() : null;
            contents.add(Map.of("role", role(message), "parts", openParts));
        }
        return contents;
    }

    private static String role(LlmMessage message) {
        return switch (message) {
            case LlmMessage.Text text -> text.role() == ChatRole.USER ? "user" : "model";
            case LlmMessage.ToolCall call -> "model";
            case LlmMessage.ToolResult result -> "user";
        };
    }

    private static Map<String, Object> part(LlmMessage message) {
        return switch (message) {
            case LlmMessage.Text text -> Map.of("text", text.text());
            case LlmMessage.ToolCall call -> {
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("functionCall", Map.of("name", call.name(), "args", call.arguments()));
                if (call.signature() != null) {
                    part.put("thoughtSignature", call.signature());
                }
                yield part;
            }
            case LlmMessage.ToolResult result ->
                    Map.of("functionResponse", Map.of("name", result.name(), "response", result.result()));
        };
    }

    // --- response ------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static LlmReply reply(Map<String, Object> response) {
        if (response == null || !(response.get("candidates") instanceof List<?> candidates) || candidates.isEmpty()
                || !(candidates.get(0) instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)) {
            throw new LlmUnavailableException("Gemini returned no answer");
        }
        StringBuilder text = new StringBuilder();
        List<LlmMessage.ToolCall> calls = new ArrayList<>();
        for (Object entry : parts) {
            if (!(entry instanceof Map<?, ?> part)) {
                continue;
            }
            if (part.get("functionCall") instanceof Map<?, ?> call && call.get("name") instanceof String name) {
                Map<String, Object> args = call.get("args") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
                String signature = part.get("thoughtSignature") instanceof String value ? value : null;
                calls.add(new LlmMessage.ToolCall(name, args, signature));
            } else if (part.get("text") instanceof String value && !Boolean.TRUE.equals(part.get("thought"))) {
                text.append(value);
            }
        }
        return calls.isEmpty() ? LlmReply.answer(text.toString()) : LlmReply.toolCalls(calls);
    }
}
