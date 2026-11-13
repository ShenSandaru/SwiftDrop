package com.swiftdrop.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.swiftdrop.assistant.application.AssistantProperties;
import com.swiftdrop.assistant.application.ChatRole;
import com.swiftdrop.assistant.application.LlmMessage;
import com.swiftdrop.assistant.application.LlmReply;
import com.swiftdrop.assistant.application.LlmUnavailableException;
import com.swiftdrop.assistant.application.ToolSpec;

class GeminiLlmClientTest {

    private static final String URL = "https://gemini.test/v1beta/models/test-model:generateContent";
    private static final ToolSpec TRACK = new ToolSpec("track_parcel", "Track a parcel.",
            Map.of("type", "object", "properties", Map.of("trackingNumber", Map.of("type", "string"))));
    private static final ToolSpec AREAS = new ToolSpec("list_service_areas", "List areas.",
            Map.of("type", "object", "properties", Map.of()));

    private MockRestServiceServer server;
    private GeminiLlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiLlmClient(builder.build(),
                new AssistantProperties("the-key", "test-model", "https://gemini.test", 3, 10, null));
    }

    private static List<LlmMessage> question() {
        return List.of(new LlmMessage.Text(ChatRole.USER, "Where is SD1?"));
    }

    @Test
    void sendsTheKeyInAHeaderAndNeverInTheUrl() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "the-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("be helpful"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Where is SD1?"))
                .andExpect(jsonPath("$.tools[0].functionDeclarations[0].name").value("track_parcel"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[{"text":"It is in Kandy."}]}}]}
                        """, MediaType.APPLICATION_JSON));

        LlmReply reply = client.generate("be helpful", question(), List.of(TRACK));

        assertThat(reply.text()).isEqualTo("It is in Kandy.");
        assertThat(reply.toolCalls()).isEmpty();
        server.verify();
    }

    @Test
    void readsAFunctionCallWithItsSignatureAndSkipsThoughtText() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[
                  {"text":"thinking...","thought":true},
                  {"functionCall":{"name":"track_parcel","args":{"trackingNumber":"SD1"}},"thoughtSignature":"sig-1"}
                ]}}]}
                """, MediaType.APPLICATION_JSON));

        LlmReply reply = client.generate("p", question(), List.of(TRACK));

        assertThat(reply.text()).isEmpty();
        assertThat(reply.toolCalls()).containsExactly(
                new LlmMessage.ToolCall("track_parcel", Map.of("trackingNumber", "SD1"), "sig-1"));
    }

    @Test
    void sendsToolCallsAndResultsBackAsOneModelTurnAndOneUserTurn() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.contents.length()").value(3))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[1].parts.length()").value(2))
                .andExpect(jsonPath("$.contents[1].parts[0].functionCall.name").value("track_parcel"))
                .andExpect(jsonPath("$.contents[1].parts[0].thoughtSignature").value("sig-1"))
                .andExpect(jsonPath("$.contents[1].parts[1].functionCall.name").value("list_service_areas"))
                .andExpect(jsonPath("$.contents[2].role").value("user"))
                .andExpect(jsonPath("$.contents[2].parts.length()").value(2))
                .andExpect(jsonPath("$.contents[2].parts[0].functionResponse.name").value("track_parcel"))
                .andExpect(jsonPath("$.contents[2].parts[0].functionResponse.response.status").value("AT_HUB"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"Done."}]}}]}
                        """, MediaType.APPLICATION_JSON));

        client.generate("p", List.of(
                new LlmMessage.Text(ChatRole.USER, "q"),
                new LlmMessage.ToolCall("track_parcel", Map.of("trackingNumber", "SD1"), "sig-1"),
                new LlmMessage.ToolCall("list_service_areas", Map.of()),
                new LlmMessage.ToolResult("track_parcel", Map.of("status", "AT_HUB")),
                new LlmMessage.ToolResult("list_service_areas", Map.of("areas", List.of("Kandy")))),
                List.of(TRACK, AREAS));

        server.verify();
    }

    @Test
    void leavesOutParametersForAToolWithoutArgumentsAndToolsWhenThereAreNone() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools[0].functionDeclarations[1].name").value("list_service_areas"))
                .andExpect(jsonPath("$.tools[0].functionDeclarations[1].parameters").doesNotExist())
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"a\"}]}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"b\"}]}}]}",
                        MediaType.APPLICATION_JSON));

        client.generate("p", question(), List.of(TRACK, AREAS));
        client.generate("p", question(), List.of());

        server.verify();
    }

    @Test
    void reportsQuotaAndServerErrorsWithoutTheResponseBody() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"leaky detail the-key\"}"));

        assertThatThrownBy(() -> client.generate("p", question(), List.of()))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessage("Gemini answered HTTP 429 for model test-model");
    }

    @Test
    void hintsAtTheModelSettingWhenGeminiDoesNotKnowTheModel() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.generate("p", question(), List.of()))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("HTTP 404 for model test-model")
                .hasMessageContaining("SWIFTDROP_ASSISTANT_MODEL");
    }

    @Test
    void rejectsAnAnswerWithoutCandidates() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate("p", question(), List.of()))
                .isInstanceOf(LlmUnavailableException.class).hasMessage("Gemini returned no answer");
    }

    @Test
    void doesNotCallGeminiWithoutAKey() {
        GeminiLlmClient keyless = new GeminiLlmClient(RestClient.builder().baseUrl("https://gemini.test").build(),
                new AssistantProperties("", "m", null, 3, 10, null));

        assertThatThrownBy(() -> keyless.generate("p", question(), List.of()))
                .isInstanceOf(LlmUnavailableException.class).hasMessageContaining("No Gemini API key");
        server.verify();
    }
}
