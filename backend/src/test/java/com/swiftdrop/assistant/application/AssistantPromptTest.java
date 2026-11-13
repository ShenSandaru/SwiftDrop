package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssistantPromptTest {

    @Test
    void keepsTheGuardrailsAndTheHowToFacts() {
        String prompt = AssistantPrompt.SYSTEM;

        // Guardrails
        assertThat(prompt).contains("Never guess", "Politely decline", "cannot see anyone's name");
        // Facts the how-to answers rely on; they must match the app and ADR 0009
        assertThat(prompt).contains("Book Delivery", "Confirm booking", "up to 3 attempts", "until the courier has collected");
    }
}
