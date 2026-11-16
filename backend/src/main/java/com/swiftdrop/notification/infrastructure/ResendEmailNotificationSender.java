package com.swiftdrop.notification.infrastructure;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.swiftdrop.notification.application.EmailNotificationSender;

/**
 * {@link EmailNotificationSender} implementation backed by the Resend HTTP API.
 * Active only when {@code swiftdrop.notification.email.provider=resend}.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.notification.email.provider", havingValue = "resend")
@EnableConfigurationProperties(ResendEmailProperties.class)
public class ResendEmailNotificationSender implements EmailNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailNotificationSender.class);

    private final RestClient restClient;
    private final ResendEmailProperties properties;

    @Autowired
    public ResendEmailNotificationSender(
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ResendEmailProperties properties) {
        this(restClientBuilderProvider.getIfAvailable(RestClient::builder), properties);
    }

    public ResendEmailNotificationSender(RestClient.Builder restClientBuilder, ResendEmailProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Resend email provider is enabled but RESEND_API_KEY (swiftdrop.notification.email.resend.api-key) is missing");
        }
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null")
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Override
    public void send(String recipientEmail, String subject, String body) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("recipientEmail must not be null or blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be null or blank");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be null or blank");
        }

        ResendEmailRequest payload = new ResendEmailRequest(
                properties.fromAddress(),
                List.of(recipientEmail),
                subject,
                body);

        try {
            ResendEmailResponse response = restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(ResendEmailResponse.class);
            String messageId = response != null && response.id() != null && !response.id().isBlank()
                    ? response.id()
                    : "unknown";
            log.info("Dispatched email via Resend (messageId={})", messageId);
        } catch (RestClientResponseException ex) {
            String details = sanitizeErrorDetails(ex.getResponseBodyAsString());
            String message = details.isEmpty()
                    ? "Resend API returned HTTP " + ex.getStatusCode().value()
                    : "Resend API returned HTTP " + ex.getStatusCode().value() + ": " + details;
            throw new IllegalStateException(message, ex);
        } catch (RestClientException ex) {
            String details = sanitizeErrorDetails(ex.getMessage());
            String message = details.isEmpty()
                    ? "Failed to send email via Resend"
                    : "Failed to send email via Resend: " + details;
            throw new IllegalStateException(message, ex);
        }
    }

    private static String sanitizeErrorDetails(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String singleLine = raw.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (singleLine.length() > 300) {
            return singleLine.substring(0, 300) + "...";
        }
        return singleLine;
    }

    private record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String text) {
    }

    private record ResendEmailResponse(String id) {
    }
}
