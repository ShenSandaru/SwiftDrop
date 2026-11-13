package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class AssistantServiceTest {

    /** Plays back prepared replies and records what it was asked. */
    private static final class ScriptedLlm implements LlmClient {
        private final List<Supplier<LlmReply>> replies;
        final List<List<LlmMessage>> conversations = new ArrayList<>();
        final List<Integer> toolCounts = new ArrayList<>();
        private int next;

        @SafeVarargs
        ScriptedLlm(Supplier<LlmReply>... replies) {
            this.replies = List.of(replies);
        }

        @Override
        public LlmReply generate(String systemPrompt, List<LlmMessage> conversation, List<ToolSpec> tools) {
            conversations.add(List.copyOf(conversation));
            toolCounts.add(tools.size());
            return replies.get(next++).get();
        }
    }

    private static AssistantTool tool(String name, AtomicInteger calls, Map<String, Object> result) {
        return new AssistantTool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(name, "test tool", Map.of("type", "object"));
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments) {
                calls.incrementAndGet();
                return result;
            }
        };
    }

    private static LlmMessage.ToolCall call(String name) {
        return new LlmMessage.ToolCall(name, Map.of("q", "x"));
    }

    private static AssistantService service(LlmClient llm, AssistantTool... tools) {
        return new AssistantService(llm, List.of(tools), new AssistantProperties("k", null, null, 3, 10, null));
    }

    @Test
    void answersDirectlyWhenTheModelNeedsNoTool() {
        ScriptedLlm llm = new ScriptedLlm(() -> LlmReply.answer(" Hello! "));

        AssistantAnswer answer = service(llm).reply("Hi", List.of());

        assertThat(answer.reply()).isEqualTo("Hello!");
        assertThat(answer.unavailable()).isFalse();
    }

    @Test
    void sendsHistoryThenTheNewMessage() {
        ScriptedLlm llm = new ScriptedLlm(() -> LlmReply.answer("ok"));

        service(llm).reply("second", List.of(new ChatTurn(ChatRole.USER, "first"),
                new ChatTurn(ChatRole.ASSISTANT, "reply")));

        assertThat(llm.conversations.get(0)).containsExactly(
                new LlmMessage.Text(ChatRole.USER, "first"),
                new LlmMessage.Text(ChatRole.ASSISTANT, "reply"),
                new LlmMessage.Text(ChatRole.USER, "second"));
    }

    @Test
    void runsARequestedToolAndGivesItsResultBackToTheModel() {
        AtomicInteger runs = new AtomicInteger();
        ScriptedLlm llm = new ScriptedLlm(
                () -> LlmReply.toolCalls(List.of(call("track_parcel"))),
                () -> LlmReply.answer("It is at the hub."));

        AssistantAnswer answer = service(llm, tool("track_parcel", runs, Map.of("status", "AT_HUB")))
                .reply("Where is SD1?", List.of());

        assertThat(answer.reply()).isEqualTo("It is at the hub.");
        assertThat(runs).hasValue(1);
        List<LlmMessage> second = llm.conversations.get(1);
        assertThat(second).contains(call("track_parcel"),
                new LlmMessage.ToolResult("track_parcel", Map.of("status", "AT_HUB")));
    }

    @Test
    void returnsWhatSuccessfulToolsFoundAsCardsInOrder() {
        ScriptedLlm llm = new ScriptedLlm(
                () -> LlmReply.toolCalls(List.of(call("track_parcel"), call("estimate_fee"), call("missing"))),
                () -> LlmReply.answer("Here you go."));

        AssistantAnswer answer = service(llm,
                tool("track_parcel", new AtomicInteger(), Map.of("found", true, "status", "AT_HUB")),
                tool("estimate_fee", new AtomicInteger(), Map.of("totalFee", "750.00")))
                .reply("x", List.of());

        assertThat(answer.cards()).containsExactly(
                new AssistantCard("track_parcel", Map.of("found", true, "status", "AT_HUB")),
                new AssistantCard("estimate_fee", Map.of("totalFee", "750.00")));
    }

    @Test
    void errorsAndUnknownParcelsDoNotBecomeCards() {
        ScriptedLlm llm = new ScriptedLlm(
                () -> LlmReply.toolCalls(List.of(call("track_parcel"), call("estimate_fee"))),
                () -> LlmReply.answer("Sorry."));

        AssistantAnswer answer = service(llm,
                tool("track_parcel", new AtomicInteger(), Map.of("found", false)),
                tool("estimate_fee", new AtomicInteger(), Map.of("error", "No price is set for that route.")))
                .reply("x", List.of());

        assertThat(answer.cards()).isEmpty();
    }

    @Test
    void tellsTheModelWhenItAsksForAToolThatDoesNotExist() {
        ScriptedLlm llm = new ScriptedLlm(
                () -> LlmReply.toolCalls(List.of(call("delete_everything"))),
                () -> LlmReply.answer("Sorry."));

        AssistantAnswer answer = service(llm).reply("x", List.of());

        assertThat(answer.reply()).isEqualTo("Sorry.");
        assertThat(llm.conversations.get(1)).contains(
                new LlmMessage.ToolResult("delete_everything", Map.of("error", "Unknown tool.")));
    }

    @Test
    void reportsAToolFailureToTheModelWithoutLeakingTheException() {
        AssistantTool broken = new AssistantTool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("track_parcel", "t", Map.of("type", "object"));
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments) {
                throw new IllegalStateException("jdbc:postgresql://secret-host");
            }
        };
        ScriptedLlm llm = new ScriptedLlm(
                () -> LlmReply.toolCalls(List.of(call("track_parcel"))),
                () -> LlmReply.answer("Try later."));

        service(llm, broken).reply("x", List.of());

        assertThat(llm.conversations.get(1).toString()).doesNotContain("secret-host");
        assertThat(llm.conversations.get(1)).contains(
                new LlmMessage.ToolResult("track_parcel", Map.of("error", "The tool failed. Try again later.")));
    }

    @Test
    void stopsAfterTheToolCallLimitAndAsksForAPlainAnswer() {
        AtomicInteger runs = new AtomicInteger();
        ScriptedLlm llm = new ScriptedLlm(
                () -> LlmReply.toolCalls(List.of(call("t"), call("t"))),
                () -> LlmReply.toolCalls(List.of(call("t"), call("t"))),
                () -> LlmReply.answer("Here is what I have."));

        AssistantAnswer answer = service(llm, tool("t", runs, Map.of("ok", true))).reply("x", List.of());

        assertThat(answer.reply()).isEqualTo("Here is what I have.");
        assertThat(runs).hasValue(2);
        assertThat(llm.toolCounts).containsExactly(1, 1, 0);
    }

    @Test
    void usesTheFallbackWhenTheModelIsUnavailable() {
        ScriptedLlm llm = new ScriptedLlm(() -> {
            throw new LlmUnavailableException("quota exceeded");
        });

        AssistantAnswer answer = service(llm).reply("x", List.of());

        assertThat(answer.unavailable()).isTrue();
        assertThat(answer.reply()).isEqualTo(AssistantPrompt.UNAVAILABLE);
        assertThat(answer.cards()).isEmpty();
    }

    @Test
    void usesTheFallbackWhenTheModelAnswersWithNothing() {
        ScriptedLlm llm = new ScriptedLlm(() -> LlmReply.answer("  "));

        assertThat(service(llm).reply("x", List.of()).unavailable()).isTrue();
    }
}
