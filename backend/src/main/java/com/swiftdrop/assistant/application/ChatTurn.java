package com.swiftdrop.assistant.application;

import java.util.Objects;

/** One earlier message of the conversation, kept by the browser and sent back with each request. */
public record ChatTurn(ChatRole role, String text) {

    public ChatTurn {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(text, "text must not be null");
    }
}
