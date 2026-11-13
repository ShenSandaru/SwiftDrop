package com.swiftdrop.assistant.application;

import java.util.Map;

/**
 * A read-only question the assistant may ask the backend (ADR 0010 §2). Tools return public data
 * only and report problems as an {@code error} entry in the result, so the model can explain them.
 */
public interface AssistantTool {

    ToolSpec spec();

    Map<String, Object> execute(Map<String, Object> arguments);
}
