package com.swiftdrop.assistant.application;

import java.util.Map;
import java.util.Objects;

/**
 * A tool as the model sees it.
 *
 * @param parameters JSON schema of the arguments (an {@code object} schema)
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {

    public ToolSpec {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
    }
}
