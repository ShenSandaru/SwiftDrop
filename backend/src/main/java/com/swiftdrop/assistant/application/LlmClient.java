package com.swiftdrop.assistant.application;

import java.util.List;

/** The language model behind the assistant (ADR 0010 §3); one implementation per provider. */
public interface LlmClient {

    /**
     * @param systemPrompt instructions for the model
     * @param conversation the conversation so far, oldest first
     * @param tools        the tools the model may call
     * @throws LlmUnavailableException if the model cannot answer now (down, over quota, bad reply)
     */
    LlmReply generate(String systemPrompt, List<LlmMessage> conversation, List<ToolSpec> tools);
}
