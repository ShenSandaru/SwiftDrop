package com.swiftdrop.assistant.application;

import java.util.List;

/**
 * The assistant's reply.
 *
 * @param unavailable {@code true} when the model could not answer and {@code reply} is the fixed
 *                    fallback message
 * @param cards       what the tools found, in the order they ran; empty when no tool succeeded
 */
public record AssistantAnswer(String reply, boolean unavailable, List<AssistantCard> cards) {

    public AssistantAnswer {
        cards = cards != null ? List.copyOf(cards) : List.of();
    }

    public AssistantAnswer(String reply, boolean unavailable) {
        this(reply, unavailable, List.of());
    }
}
