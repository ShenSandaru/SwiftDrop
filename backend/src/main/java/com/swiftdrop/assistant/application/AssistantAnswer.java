package com.swiftdrop.assistant.application;

/**
 * The assistant's reply.
 *
 * @param unavailable {@code true} when the model could not answer and {@code reply} is the fixed
 *                    fallback message
 */
public record AssistantAnswer(String reply, boolean unavailable) {
}
