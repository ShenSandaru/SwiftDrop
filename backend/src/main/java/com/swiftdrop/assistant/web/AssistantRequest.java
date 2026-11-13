package com.swiftdrop.assistant.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.swiftdrop.assistant.application.ChatRole;

/**
 * A customer message and the conversation so far (ADR 0010 §4). The browser keeps the history; the
 * limits keep one request, and the cost of answering it, small.
 */
record AssistantRequest(
        @NotBlank @Size(max = AssistantRequest.MAX_TEXT_LENGTH) String message,
        @Size(max = AssistantRequest.MAX_HISTORY_TURNS) List<@NotNull @Valid Turn> history) {

    static final int MAX_TEXT_LENGTH = 500;
    static final int MAX_HISTORY_TURNS = 10;

    record Turn(
            @NotNull ChatRole role,
            @NotBlank @Size(max = MAX_TEXT_LENGTH) String text) {
    }

    List<Turn> historyOrEmpty() {
        return history != null ? history : List.of();
    }
}
