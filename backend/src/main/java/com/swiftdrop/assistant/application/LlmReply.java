package com.swiftdrop.assistant.application;

import java.util.List;

/**
 * What the model answered: either final text, or tool calls it wants run first.
 *
 * @param text      the answer, empty when the model asked for tools
 * @param toolCalls the tools to run, empty when the model answered
 */
public record LlmReply(String text, List<LlmMessage.ToolCall> toolCalls) {

    public LlmReply {
        text = text != null ? text : "";
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    public static LlmReply answer(String text) {
        return new LlmReply(text, List.of());
    }

    public static LlmReply toolCalls(List<LlmMessage.ToolCall> calls) {
        return new LlmReply("", calls);
    }
}
