package com.swiftdrop.assistant.application;

import java.util.Map;
import java.util.Objects;

/** One entry of the conversation sent to the model: text, a tool call the model made, or its result. */
public sealed interface LlmMessage {

    record Text(ChatRole role, String text) implements LlmMessage {
        public Text {
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(text, "text must not be null");
        }
    }

    /**
     * @param signature opaque value some models attach to a tool call and require back with the
     *                  result; {@code null} when there is none
     */
    record ToolCall(String name, Map<String, Object> arguments, String signature) implements LlmMessage {
        public ToolCall {
            Objects.requireNonNull(name, "name must not be null");
            arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }

        public ToolCall(String name, Map<String, Object> arguments) {
            this(name, arguments, null);
        }
    }

    record ToolResult(String name, Map<String, Object> result) implements LlmMessage {
        public ToolResult {
            Objects.requireNonNull(name, "name must not be null");
            result = result != null ? Map.copyOf(result) : Map.of();
        }
    }
}
