package com.swiftdrop.assistant.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Answers one customer message: asks the model, runs the tools it asks for, and asks again until it
 * gives a text answer (ADR 0010 §4). Logs only tool names and outcomes, never message text.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final LlmClient llm;
    private final Map<String, AssistantTool> tools;
    private final int maxToolCalls;

    AssistantService(LlmClient llm, List<AssistantTool> tools, AssistantProperties properties) {
        this.llm = llm;
        this.tools = tools.stream().collect(Collectors.toMap(tool -> tool.spec().name(), Function.identity()));
        this.maxToolCalls = properties.maxToolCalls();
    }

    public AssistantAnswer reply(String message, List<ChatTurn> history) {
        List<LlmMessage> conversation = new ArrayList<>();
        history.forEach(turn -> conversation.add(new LlmMessage.Text(turn.role(), turn.text())));
        conversation.add(new LlmMessage.Text(ChatRole.USER, message));
        List<AssistantCard> cards = new ArrayList<>();
        try {
            return new AssistantAnswer(answer(conversation, cards), false, cards);
        } catch (LlmUnavailableException e) {
            log.warn("Assistant model unavailable: {}", e.getMessage());
            return new AssistantAnswer(AssistantPrompt.UNAVAILABLE, true);
        }
    }

    private String answer(List<LlmMessage> conversation, List<AssistantCard> cards) {
        List<ToolSpec> specs = tools.values().stream().map(AssistantTool::spec).toList();
        int toolCalls = 0;
        while (true) {
            LlmReply reply = llm.generate(AssistantPrompt.SYSTEM, conversation, specs);
            if (reply.toolCalls().isEmpty()) {
                return nonBlank(reply.text());
            }
            if (toolCalls + reply.toolCalls().size() > maxToolCalls) {
                // Too many tool calls for one message: ask once more, without tools, for a plain answer
                log.warn("Assistant stopped after {} tool calls", toolCalls);
                LlmReply last = llm.generate(AssistantPrompt.SYSTEM, conversation, List.of());
                return nonBlank(last.toolCalls().isEmpty() ? last.text() : "");
            }
            for (LlmMessage.ToolCall call : reply.toolCalls()) {
                conversation.add(call);
                Map<String, Object> result = run(call);
                conversation.add(new LlmMessage.ToolResult(call.name(), result));
                if (isCardWorthy(result)) {
                    cards.add(new AssistantCard(call.name(), result));
                }
                toolCalls++;
            }
        }
    }

    /** Errors and "not found" stay in the text answer; only real findings become cards. */
    private static boolean isCardWorthy(Map<String, Object> result) {
        return !result.containsKey("error") && !Boolean.FALSE.equals(result.get("found"));
    }

    private Map<String, Object> run(LlmMessage.ToolCall call) {
        AssistantTool tool = tools.get(call.name());
        if (tool == null) {
            log.warn("Assistant model asked for an unknown tool");
            return Map.of("error", "Unknown tool.");
        }
        long started = System.nanoTime();
        try {
            Map<String, Object> result = tool.execute(call.arguments());
            log.info("Assistant tool {} finished in {} ms", call.name(), (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (RuntimeException e) {
            log.warn("Assistant tool {} failed: {}", call.name(), e.getClass().getSimpleName());
            return Map.of("error", "The tool failed. Try again later.");
        }
    }

    private static String nonBlank(String text) {
        if (text == null || text.isBlank()) {
            throw new LlmUnavailableException("The model returned no text");
        }
        return text.trim();
    }
}
