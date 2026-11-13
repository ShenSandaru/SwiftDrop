package com.swiftdrop.assistant.web;

/** The assistant's reply; {@code unavailable} is true when {@code reply} is the fixed fallback text. */
record AssistantResponse(String reply, boolean unavailable) {
}
