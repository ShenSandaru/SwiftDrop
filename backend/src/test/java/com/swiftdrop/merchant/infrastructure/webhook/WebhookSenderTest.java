package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookAttempt;
import com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookDelivery;
import com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.WebhookAttemptOutcome;
import com.swiftdrop.merchant.domain.WebhookDeliveryStatus;
import com.swiftdrop.merchant.infrastructure.webhook.WebhookCallResult.Outcome;
import com.swiftdrop.merchant.security.SecretCipher;

/** Outcome classification, backoff, send-time checks and lease handling (ADR 0004 §7.1, §7.2, §9, §11). */
class WebhookSenderTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final UUID DELIVERY_ID = UUID.randomUUID();
    private static final UUID ENDPOINT_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String SECRET = "whsec_endpoint-signing-secret";
    private static final String PAYLOAD = "{\"eventId\":\"" + EVENT_ID + "\", \"spacing\": \"as received\"}";
    private static final String URL = "https://hooks.example.com/swiftdrop";

    private final MerchantWebhookDeliveryRepository deliveries = mock(MerchantWebhookDeliveryRepository.class);
    private final MerchantWebhookAttemptRepository attempts = mock(MerchantWebhookAttemptRepository.class);
    private final MerchantWebhookEndpointRepository endpoints = mock(MerchantWebhookEndpointRepository.class);
    private final MerchantRepository merchants = mock(MerchantRepository.class);
    private final SecretCipher cipher = new SecretCipher("test-only-webhook-secret-key-not-used-outside-tests");
    private final WebhookHttpClient http = mock(WebhookHttpClient.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private final MerchantWebhookDelivery delivery = mock(MerchantWebhookDelivery.class);
    private final MerchantWebhookEndpoint endpoint = mock(MerchantWebhookEndpoint.class);
    private Merchant merchant;
    private final UUID token = UUID.randomUUID();

    /** nextDouble() = 0.5, so the jitter is zero and delays are exact. */
    private static final RandomGenerator NO_JITTER = () -> Long.MIN_VALUE;

    private WebhookSender sender(RandomGenerator random) {
        return new WebhookSender(deliveries, attempts, endpoints, merchants, cipher, http,
                new WebhookSenderProperties(null, null, null, null, null, null, null),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), meters,
                Clock.fixed(NOW, ZoneOffset.UTC), random);
    }

    @BeforeEach
    void setUp() {
        when(delivery.getId()).thenReturn(DELIVERY_ID);
        when(delivery.getEndpointId()).thenReturn(ENDPOINT_ID);
        when(delivery.getMerchantId()).thenReturn(MERCHANT_ID);
        when(delivery.getEventId()).thenReturn(EVENT_ID);
        when(delivery.getPayload()).thenReturn(PAYLOAD);
        when(delivery.getNextAttemptAt()).thenReturn(NOW.minusSeconds(5));
        // A new record: no previous outcome (a mock would answer 0 for the Integer)
        when(delivery.getLastHttpStatus()).thenReturn(null);

        SecretCipher.EncryptedSecret encrypted = cipher.encrypt(SECRET);
        when(endpoint.getMerchantId()).thenReturn(MERCHANT_ID);
        when(endpoint.isEnabled()).thenReturn(true);
        when(endpoint.getUrl()).thenReturn(URL);
        when(endpoint.getSecretCiphertext()).thenReturn(encrypted.ciphertext());
        when(endpoint.getSecretIv()).thenReturn(encrypted.iv());
        when(endpoint.getSecretKeyVersion()).thenReturn(encrypted.keyVersion());
        when(endpoints.findById(ENDPOINT_ID)).thenReturn(Optional.of(endpoint));

        merchant = new Merchant(UUID.randomUUID(), "Apex", "Retail", "REG-1", "+94771234567", "1 Main St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        merchant.approve(UUID.randomUUID());
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        when(deliveries.finishAttempt(any(), any(), any(), anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(1);
    }

    private void respond(Outcome outcome, Integer status, String retryAfter) {
        when(http.post(anyString(), any(), any())).thenReturn(
                new WebhookCallResult(outcome, status, Duration.ofMillis(120), retryAfter));
    }

    private void verifyFinished(WebhookDeliveryStatus status, int increment, Instant next, Instant completed,
            WebhookAttemptOutcome outcome, Integer httpStatus) {
        verify(deliveries).finishAttempt(eq(DELIVERY_ID), eq(token), eq(NOW), eq(status.name()), eq(increment),
                eq(next), completed == null ? isNull() : eq(completed), eq(outcome == null ? null : outcome.name()),
                httpStatus == null ? isNull() : eq(httpStatus));
    }

    private MerchantWebhookAttempt savedAttempt() {
        ArgumentCaptor<MerchantWebhookAttempt> saved = ArgumentCaptor.forClass(MerchantWebhookAttempt.class);
        verify(attempts).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("2xx: SUCCEEDED, attempt 1 recorded, body is the stored payload bytes, signature verifies")
    @SuppressWarnings("unchecked")
    void success() {
        respond(Outcome.RESPONDED, 204, null);

        sender(NO_JITTER).send(delivery, token);

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(http).post(eq(URL), headers.capture(), body.capture());
        assertThat(body.getValue()).isEqualTo(PAYLOAD.getBytes(StandardCharsets.UTF_8));
        assertThat(headers.getValue())
                .containsEntry(WebhookSignature.EVENT_ID_HEADER, EVENT_ID.toString())
                .containsEntry(WebhookSignature.EVENT_TYPE_HEADER, "parcel.status-changed")
                .containsEntry(WebhookSignature.TIMESTAMP_HEADER, Long.toString(NOW.getEpochSecond()));
        assertThat(WebhookSignature.verify(headers.getValue().get(WebhookSignature.SIGNATURE_HEADER), SECRET,
                headers.getValue().get(WebhookSignature.TIMESTAMP_HEADER), body.getValue(), NOW,
                WebhookSignature.DEFAULT_TOLERANCE)).isTrue();

        verifyFinished(WebhookDeliveryStatus.SUCCEEDED, 1, NOW.minusSeconds(5), NOW, WebhookAttemptOutcome.SUCCEEDED, 204);
        MerchantWebhookAttempt attempt = savedAttempt();
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getOutcome()).isEqualTo(WebhookAttemptOutcome.SUCCEEDED);
        assertThat(attempt.getHttpStatus()).isEqualTo(204);
        assertThat(attempt.getDurationMs()).isEqualTo(120);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 408, 429})
    @DisplayName("5xx, 408 and 429 are retried after 30 s on the first failure")
    void retryableStatus(int status) {
        respond(Outcome.RESPONDED, status, null);

        sender(NO_JITTER).send(delivery, token);

        verifyFinished(WebhookDeliveryStatus.PENDING, 1, NOW.plusSeconds(30), null, WebhookAttemptOutcome.HTTP_ERROR, status);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 400, 401, 403, 404, 410, 422})
    @DisplayName("3xx and other 4xx fail at once")
    void permanentStatus(int status) {
        respond(Outcome.RESPONDED, status, null);

        sender(NO_JITTER).send(delivery, token);

        verifyFinished(WebhookDeliveryStatus.FAILED, 1, NOW.minusSeconds(5), NOW, WebhookAttemptOutcome.HTTP_ERROR, status);
    }

    @Test
    @DisplayName("Timeouts and network errors are retried; a refused destination fails at once")
    void transportOutcomes() {
        respond(Outcome.TIMEOUT, null, null);
        sender(NO_JITTER).send(delivery, token);
        verifyFinished(WebhookDeliveryStatus.PENDING, 1, NOW.plusSeconds(30), null, WebhookAttemptOutcome.TIMEOUT, null);

        respond(Outcome.NETWORK_ERROR, null, null);
        sender(NO_JITTER).send(delivery, token);
        verifyFinished(WebhookDeliveryStatus.PENDING, 1, NOW.plusSeconds(30), null, WebhookAttemptOutcome.NETWORK_ERROR,
                null);

        respond(Outcome.REFUSED_DESTINATION, null, null);
        sender(NO_JITTER).send(delivery, token);
        verifyFinished(WebhookDeliveryStatus.FAILED, 1, NOW.minusSeconds(5), NOW,
                WebhookAttemptOutcome.REFUSED_DESTINATION, null);
    }

    @Test
    @DisplayName("Backoff doubles per attempt and stops at the 1 h cap; attempt numbers follow the record")
    void backoff() {
        respond(Outcome.RESPONDED, 500, null);
        when(delivery.getAttempts()).thenReturn(3);

        sender(NO_JITTER).send(delivery, token);

        verifyFinished(WebhookDeliveryStatus.PENDING, 1, NOW.plusSeconds(240), null, WebhookAttemptOutcome.HTTP_ERROR, 500);
        assertThat(savedAttempt().getAttemptNumber()).isEqualTo(4);
        assertThat(sender(NO_JITTER).nextDelay(7, null, NOW)).isEqualTo(Duration.ofMinutes(32));
        assertThat(sender(NO_JITTER).nextDelay(8, null, NOW)).as("64 min, capped").isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("Jitter stays within ±20 %")
    void jitterBounds() {
        assertThat(sender(() -> 0L).nextDelay(1, null, NOW)).isEqualTo(Duration.ofSeconds(24));
        assertThat(sender(() -> -1L).nextDelay(1, null, NOW)).isBetween(Duration.ofSeconds(35), Duration.ofSeconds(36));
    }

    @Test
    @DisplayName("The 8th failed attempt fails the record")
    void attemptLimit() {
        respond(Outcome.RESPONDED, 503, null);
        when(delivery.getAttempts()).thenReturn(7);

        sender(NO_JITTER).send(delivery, token);

        verifyFinished(WebhookDeliveryStatus.FAILED, 1, NOW.minusSeconds(5), NOW, WebhookAttemptOutcome.HTTP_ERROR, 503);
        assertThat(savedAttempt().getAttemptNumber()).isEqualTo(8);
    }

    @Test
    @DisplayName("The attempt limit counts the current series only: a manual series gets 8 attempts again")
    void limitPerSeries() {
        respond(Outcome.RESPONDED, 503, null);
        UUID requestedBy = UUID.randomUUID();
        // Series 1 started after 8 automatic attempts; this is its 3rd attempt (attempt 11 overall)
        when(delivery.getAttempts()).thenReturn(10);
        when(delivery.getSeriesStartAttempts()).thenReturn(8);
        when(delivery.getManualRedeliveries()).thenReturn(1);
        when(delivery.getSeriesTriggeredBy()).thenReturn(requestedBy);

        sender(NO_JITTER).send(delivery, token);

        // 3rd attempt of the series: backoff 30 s × 2² = 120 s, not the overall attempt 11's cap
        verifyFinished(WebhookDeliveryStatus.PENDING, 1, NOW.plusSeconds(120), null, WebhookAttemptOutcome.HTTP_ERROR,
                503);
        MerchantWebhookAttempt attempt = savedAttempt();
        assertThat(attempt.getAttemptNumber()).isEqualTo(11);
        assertThat(attempt.getSeries()).isEqualTo(1);
        assertThat(attempt.getTriggeredByUserId()).isEqualTo(requestedBy);
    }

    @Test
    @DisplayName("The 8th attempt of a manual series fails the record")
    void limitReachedInManualSeries() {
        respond(Outcome.RESPONDED, 503, null);
        when(delivery.getAttempts()).thenReturn(15);
        when(delivery.getSeriesStartAttempts()).thenReturn(8);
        when(delivery.getManualRedeliveries()).thenReturn(1);

        sender(NO_JITTER).send(delivery, token);

        verifyFinished(WebhookDeliveryStatus.FAILED, 1, NOW.minusSeconds(5), NOW, WebhookAttemptOutcome.HTTP_ERROR, 503);
        assertThat(savedAttempt().getAttemptNumber()).isEqualTo(16);
    }

    @Test
    @DisplayName("Automatic attempts are series 0 without a requesting user")
    void automaticSeries() {
        respond(Outcome.RESPONDED, 200, null);

        sender(NO_JITTER).send(delivery, token);

        MerchantWebhookAttempt attempt = savedAttempt();
        assertThat(attempt.getSeries()).isZero();
        assertThat(attempt.getTriggeredByUserId()).isNull();
    }

    @Test
    @DisplayName("Retry-After in seconds or as an HTTP-date is honored up to the 1 h cap; invalid or past values are ignored")
    void retryAfter() {
        WebhookSender sender = sender(NO_JITTER);
        String inTenMinutes = DateTimeFormatter.RFC_1123_DATE_TIME.format(NOW.plusSeconds(600).atZone(ZoneOffset.UTC));
        String inThePast = DateTimeFormatter.RFC_1123_DATE_TIME.format(NOW.minusSeconds(600).atZone(ZoneOffset.UTC));

        assertThat(sender.nextDelay(1, "120", NOW)).isEqualTo(Duration.ofSeconds(120));
        assertThat(sender.nextDelay(1, " 120 ", NOW)).isEqualTo(Duration.ofSeconds(120));
        assertThat(sender.nextDelay(1, "10", NOW)).as("shorter than the backoff").isEqualTo(Duration.ofSeconds(30));
        assertThat(sender.nextDelay(1, "7200", NOW)).isEqualTo(Duration.ofHours(1));
        assertThat(sender.nextDelay(1, "99999999999999999999999", NOW)).isEqualTo(Duration.ofHours(1));
        assertThat(sender.nextDelay(1, inTenMinutes, NOW)).isEqualTo(Duration.ofSeconds(600));
        assertThat(sender.nextDelay(1, inThePast, NOW)).isEqualTo(Duration.ofSeconds(30));
        assertThat(sender.nextDelay(1, "-5", NOW)).isEqualTo(Duration.ofSeconds(30));
        assertThat(sender.nextDelay(1, "soon", NOW)).isEqualTo(Duration.ofSeconds(30));
        assertThat(sender.nextDelay(1, "", NOW)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("A 429 with Retry-After waits as asked")
    void retryAfterOnResponse() {
        respond(Outcome.RESPONDED, 429, "300");

        sender(NO_JITTER).send(delivery, token);

        verifyFinished(WebhookDeliveryStatus.PENDING, 1, NOW.plusSeconds(300), null, WebhookAttemptOutcome.HTTP_ERROR, 429);
    }

    @Test
    @DisplayName("A deleted, disabled, missing or other merchant's endpoint fails the record without a request")
    void inactiveEndpoint() {
        when(endpoint.isEnabled()).thenReturn(false);
        sender(NO_JITTER).send(delivery, token);

        when(endpoint.isEnabled()).thenReturn(true);
        when(endpoint.isDeleted()).thenReturn(true);
        sender(NO_JITTER).send(delivery, token);

        when(endpoint.isDeleted()).thenReturn(false);
        when(endpoint.getMerchantId()).thenReturn(UUID.randomUUID());
        sender(NO_JITTER).send(delivery, token);

        when(endpoints.findById(ENDPOINT_ID)).thenReturn(Optional.empty());
        sender(NO_JITTER).send(delivery, token);

        verify(deliveries, org.mockito.Mockito.times(4)).finishAttempt(eq(DELIVERY_ID), eq(token), eq(NOW),
                eq("FAILED"), eq(1), any(), eq(NOW), eq("ENDPOINT_INACTIVE"), isNull());
        verify(http, never()).post(anyString(), any(), any());
    }

    @Test
    @DisplayName("A suspended or missing merchant fails the record without a request")
    void merchantNotApprovedOrMissing() {
        merchant.suspend(UUID.randomUUID(), "Review");
        sender(NO_JITTER).send(delivery, token);
        verifyFinished(WebhookDeliveryStatus.FAILED, 1, NOW.minusSeconds(5), NOW,
                WebhookAttemptOutcome.MERCHANT_NOT_APPROVED, null);

        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.empty());
        sender(NO_JITTER).send(delivery, token);
        verifyFinished(WebhookDeliveryStatus.FAILED, 1, NOW.minusSeconds(5), NOW,
                WebhookAttemptOutcome.MERCHANT_NOT_FOUND, null);

        verify(http, never()).post(anyString(), any(), any());
    }

    @Test
    @DisplayName("An undecryptable secret parks the record as SECRET_UNAVAILABLE without counting an attempt")
    void secretUnavailable() {
        when(endpoint.getSecretCiphertext()).thenReturn("dGFtcGVyZWQ=");

        sender(NO_JITTER).send(delivery, token);

        verify(deliveries).finishAttempt(eq(DELIVERY_ID), eq(token), eq(NOW), eq("SECRET_UNAVAILABLE"), eq(0), any(),
                isNull(), isNull(), isNull());
        verify(attempts, never()).save(any());
        verify(http, never()).post(anyString(), any(), any());
        assertThat(meters.counter("swiftdrop.merchant.webhooks.secret.unavailable").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("When the lease was lost (taken over or expired) nothing is written")
    void leaseLost() {
        respond(Outcome.RESPONDED, 200, null);
        when(deliveries.finishAttempt(any(), any(), any(), anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(0);

        sender(NO_JITTER).send(delivery, token);

        verify(attempts, never()).save(any());
        assertThat(meters.get("swiftdrop.merchant.webhooks.attempts").tag("outcome", "SUCCEEDED").counter().count())
                .isZero();
    }

    @Test
    @DisplayName("A poll claims due records with one fresh token and a lease, then sends each")
    void pollClaimsWithLease() {
        when(deliveries.lockDueIds(NOW, 20)).thenReturn(List.of(DELIVERY_ID));
        when(deliveries.findAllById(List.of(DELIVERY_ID))).thenReturn(List.of(delivery));
        respond(Outcome.RESPONDED, 200, null);

        sender(NO_JITTER).sendDue();

        ArgumentCaptor<UUID> claimToken = ArgumentCaptor.forClass(UUID.class);
        verify(deliveries).claim(eq(List.of(DELIVERY_ID)), claimToken.capture(), eq(NOW.plus(Duration.ofMinutes(2))));
        verify(deliveries).finishAttempt(eq(DELIVERY_ID), eq(claimToken.getValue()), eq(NOW), eq("SUCCEEDED"), eq(1),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("One failing record does not stop the batch")
    void failureDoesNotStopTheBatch() {
        MerchantWebhookDelivery broken = mock(MerchantWebhookDelivery.class);
        when(broken.getEndpointId()).thenThrow(new IllegalStateException("broken row"));
        when(deliveries.lockDueIds(NOW, 20)).thenReturn(List.of(UUID.randomUUID(), DELIVERY_ID));
        when(deliveries.findAllById(any())).thenReturn(List.of(broken, delivery));
        respond(Outcome.RESPONDED, 200, null);

        sender(NO_JITTER).sendDue();

        verify(http).post(eq(URL), any(), any());
    }
}
