package com.swiftdrop.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.swiftdrop.notification.application.MockEmailNotificationSender;

class ResendEmailNotificationSenderTest {

    private static final String TEST_API_KEY = "re_test_dummy_key_12345";
    private static final String TEST_FROM = "onboarding@resend.dev";

    private MockRestServiceServer mockServer;
    private ResendEmailNotificationSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ResendEmailProperties properties = new ResendEmailProperties(
                TEST_API_KEY,
                TEST_FROM,
                ResendEmailProperties.DEFAULT_BASE_URL);
        sender = new ResendEmailNotificationSender(builder, properties);
    }

    @Test
    @DisplayName("Sends POST /emails with Bearer auth and expected JSON payload")
    void sendsEmailSuccessfully() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value(TEST_FROM))
                .andExpect(jsonPath("$.to[0]").value("customer@example.com"))
                .andExpect(jsonPath("$.subject").value("Parcel Booked"))
                .andExpect(jsonPath("$.text").value("Your parcel SD123 has been booked."))
                .andRespond(withSuccess("{\"id\":\"email_123\"}", MediaType.APPLICATION_JSON));

        sender.send("customer@example.com", "Parcel Booked", "Your parcel SD123 has been booked.");

        mockServer.verify();
    }

    @Test
    @DisplayName("Throws exception with diagnostic details on HTTP 403 response without exposing API key")
    void handles403Response() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withForbiddenRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"You can only send testing emails to your own email address\"}"));

        assertThatThrownBy(() -> sender.send("customer@example.com", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("You can only send testing emails to your own email address")
                .hasMessageNotContaining(TEST_API_KEY);

        mockServer.verify();
    }

    @Test
    @DisplayName("Throws exception with diagnostic details on HTTP 422 response without exposing API key")
    void handles422Response() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Invalid from address\"}"));

        assertThatThrownBy(() -> sender.send("customer@example.com", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("422")
                .hasMessageContaining("Invalid from address")
                .hasMessageNotContaining(TEST_API_KEY);

        mockServer.verify();
    }

    @Test
    @DisplayName("Throws exception on HTTP 5xx response without exposing API key")
    void handles5xxResponse() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> sender.send("customer@example.com", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500")
                .hasMessageNotContaining(TEST_API_KEY);

        mockServer.verify();
    }

    @Test
    @DisplayName("Throws exception on network failure")
    void handlesNetworkFailure() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection refused", new IOException("Connection refused"));
                });

        assertThatThrownBy(() -> sender.send("customer@example.com", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to send email via Resend")
                .hasMessageNotContaining(TEST_API_KEY);

        mockServer.verify();
    }

    @Test
    @DisplayName("MockEmailNotificationSender remains the default when no provider is configured")
    void mockProviderIsDefault() {
        ConditionalOnProperty annotation = MockEmailNotificationSender.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).containsExactly("swiftdrop.notification.email.provider");
        assertThat(annotation.havingValue()).isEqualTo("mock");
        assertThat(annotation.matchIfMissing()).isTrue();
    }
}
