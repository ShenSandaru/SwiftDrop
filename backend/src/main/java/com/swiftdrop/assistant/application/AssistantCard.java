package com.swiftdrop.assistant.application;

import java.util.Map;
import java.util.Objects;

/**
 * Data a tool found while answering, so the chat can show it as a card next to the text (a
 * tracking pass, a fare ticket, the service areas). Same public data the tool gave the model.
 *
 * @param tool the tool's name, such as {@code track_parcel}
 */
public record AssistantCard(String tool, Map<String, Object> data) {

    public AssistantCard {
        Objects.requireNonNull(tool, "tool must not be null");
        data = data != null ? Map.copyOf(data) : Map.of();
    }
}
