package com.swiftdrop.assistant.web;

import java.util.List;
import java.util.Map;

import com.swiftdrop.assistant.application.AssistantAnswer;

/**
 * The assistant's reply; {@code unavailable} is true when {@code reply} is the fixed fallback text.
 * {@code cards} carry what the tools found (public data only) so the chat can show it visually.
 */
record AssistantResponse(String reply, boolean unavailable, List<Card> cards) {

    record Card(String tool, Map<String, Object> data) {
    }

    static AssistantResponse from(AssistantAnswer answer) {
        return new AssistantResponse(answer.reply(), answer.unavailable(),
                answer.cards().stream().map(card -> new Card(card.tool(), card.data())).toList());
    }
}
