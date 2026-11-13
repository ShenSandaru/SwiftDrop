package com.swiftdrop.merchant.infrastructure.webhook;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
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
import com.swiftdrop.merchant.security.SecretCipher;

/**
 * Sends webhook delivery records (SD-411, ADR 0004 §7.1, §7.2). Each poll claims due records with a fresh lease
 * token ({@code SKIP LOCKED}, so several senders never share a record) and commits; then each record is sent
 * without holding a transaction, and its outcome is stored only if this sender still holds a valid lease.
 *
 * <p>Before sending, the endpoint must be enabled, not deleted and the record's merchant's, and the merchant must
 * exist and be {@code APPROVED}; otherwise the record fails without a request. A secret that cannot be decrypted
 * makes the record {@code SECRET_UNAVAILABLE} without counting an attempt; such records are not picked up again
 * until someone re-queues them. The body is the stored payload's UTF-8 bytes, signed and sent unchanged.
 *
 * <p>2xx succeeds; timeouts, network errors, 5xx, 408 and 429 are retried with exponential backoff and jitter
 * ({@code Retry-After} honored up to the cap) until the attempt limit; everything else fails at once.
 */
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);

    private final MerchantWebhookDeliveryRepository deliveries;
    private final MerchantWebhookAttemptRepository attempts;
    private final MerchantWebhookEndpointRepository endpoints;
    private final MerchantRepository merchants;
    private final SecretCipher secrets;
    private final WebhookHttpClient http;
    private final WebhookSenderProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final RandomGenerator random;
    private final Map<WebhookAttemptOutcome, Counter> attemptCounters = new LinkedHashMap<>();
    private final Counter secretUnavailable;

    public WebhookSender(MerchantWebhookDeliveryRepository deliveries, MerchantWebhookAttemptRepository attempts,
            MerchantWebhookEndpointRepository endpoints, MerchantRepository merchants, SecretCipher secrets,
            WebhookHttpClient http, WebhookSenderProperties properties, TransactionTemplate transactions,
            MeterRegistry meters, Clock clock, RandomGenerator random) {
        this.deliveries = deliveries;
        this.attempts = attempts;
        this.endpoints = endpoints;
        this.merchants = merchants;
        this.secrets = secrets;
        this.http = http;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
        this.random = random;
        for (WebhookAttemptOutcome outcome : WebhookAttemptOutcome.values()) {
            attemptCounters.put(outcome, Counter.builder("swiftdrop.merchant.webhooks.attempts")
                    .tag("outcome", outcome.name()).description("Webhook attempts by outcome").register(meters));
        }
        secretUnavailable = Counter.builder("swiftdrop.merchant.webhooks.secret.unavailable")
                .description("Webhook records whose signing secret could not be decrypted").register(meters);
    }

    /** One scheduled run: claims up to the batch size of due records and sends each. */
    public void sendDue() {
        UUID token = UUID.randomUUID();
        List<MerchantWebhookDelivery> claimed = transactions.execute(status -> claim(token));
        for (MerchantWebhookDelivery delivery : claimed == null ? List.<MerchantWebhookDelivery>of() : claimed) {
            try {
                send(delivery, token);
            } catch (RuntimeException e) {
                // The lease expires and another poll retries the record.
                log.warn("Webhook send failed unexpectedly: deliveryId={}, cause={}", delivery.getId(),
                        e.getClass().getSimpleName());
            }
        }
    }

    private List<MerchantWebhookDelivery> claim(UUID token) {
        Instant now = clock.instant();
        List<UUID> ids = deliveries.lockDueIds(now, properties.batchSize());
        if (ids.isEmpty()) {
            return List.of();
        }
        deliveries.claim(ids, token, now.plus(properties.lease()));
        return deliveries.findAllById(ids);
    }

    void send(MerchantWebhookDelivery delivery, UUID token) {
        Optional<MerchantWebhookEndpoint> endpoint = endpoints.findById(delivery.getEndpointId())
                .filter(found -> !found.isDeleted() && found.isEnabled()
                        && found.getMerchantId().equals(delivery.getMerchantId()));
        if (endpoint.isEmpty()) {
            finish(delivery, token, WebhookAttemptOutcome.ENDPOINT_INACTIVE, null, Duration.ZERO, null);
            return;
        }
        Optional<Merchant> merchant = merchants.findById(delivery.getMerchantId());
        if (merchant.isEmpty()) {
            finish(delivery, token, WebhookAttemptOutcome.MERCHANT_NOT_FOUND, null, Duration.ZERO, null);
            return;
        }
        if (!merchant.get().isApproved()) {
            finish(delivery, token, WebhookAttemptOutcome.MERCHANT_NOT_APPROVED, null, Duration.ZERO, null);
            return;
        }
        String secret;
        try {
            secret = secrets.decrypt(endpoint.get().getSecretCiphertext(), endpoint.get().getSecretIv(),
                    endpoint.get().getSecretKeyVersion());
        } catch (RuntimeException e) {
            markSecretUnavailable(delivery, token);
            return;
        }

        byte[] body = delivery.getPayload().getBytes(StandardCharsets.UTF_8);
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(WebhookSignature.EVENT_ID_HEADER, delivery.getEventId().toString());
        headers.put(WebhookSignature.EVENT_TYPE_HEADER, ParcelStatusChanged.EVENT_TYPE);
        headers.put(WebhookSignature.TIMESTAMP_HEADER, Long.toString(timestamp));
        headers.put(WebhookSignature.SIGNATURE_HEADER, WebhookSignature.sign(secret, timestamp, body));

        WebhookCallResult result = http.post(endpoint.get().getUrl(), headers, body);
        finish(delivery, token, outcomeOf(result), result.httpStatus(), result.duration(), result);
    }

    private void finish(MerchantWebhookDelivery delivery, UUID token, WebhookAttemptOutcome outcome,
            Integer httpStatus, Duration duration, WebhookCallResult result) {
        Instant now = clock.instant();
        int attemptNumber = delivery.getAttempts() + 1;
        WebhookDeliveryStatus status;
        Instant nextAttemptAt = delivery.getNextAttemptAt();
        Instant completedAt = now;
        if (outcome == WebhookAttemptOutcome.SUCCEEDED) {
            status = WebhookDeliveryStatus.SUCCEEDED;
        } else if (result != null && isRetryable(result) && attemptNumber < properties.maxAttempts()) {
            status = WebhookDeliveryStatus.PENDING;
            nextAttemptAt = now.plus(nextDelay(attemptNumber, result.retryAfter(), now));
            completedAt = null;
        } else {
            status = WebhookDeliveryStatus.FAILED;
        }
        WebhookDeliveryStatus finalStatus = status;
        Instant finalNext = nextAttemptAt;
        Instant finalCompleted = completedAt;
        Boolean stored = transactions.execute(tx -> {
            int updated = deliveries.finishAttempt(delivery.getId(), token, now, finalStatus.name(), 1, finalNext,
                    finalCompleted, outcome.name(), httpStatus);
            if (updated == 0) {
                return false;
            }
            attempts.save(new MerchantWebhookAttempt(delivery.getId(), attemptNumber, now, outcome, httpStatus,
                    (int) Math.min(duration.toMillis(), Integer.MAX_VALUE), null));
            return true;
        });
        if (!Boolean.TRUE.equals(stored)) {
            log.warn("Webhook lease lost, outcome not stored: deliveryId={}, eventId={}, attempt={}, outcome={}",
                    delivery.getId(), delivery.getEventId(), attemptNumber, outcome);
            return;
        }
        attemptCounters.get(outcome).increment();
        log.info("Webhook attempt: deliveryId={}, eventId={}, endpointId={}, attempt={}, outcome={}, httpStatus={}, "
                + "status={}", delivery.getId(), delivery.getEventId(), delivery.getEndpointId(), attemptNumber, outcome,
                httpStatus, finalStatus);
    }

    private void markSecretUnavailable(MerchantWebhookDelivery delivery, UUID token) {
        Instant now = clock.instant();
        Integer updated = transactions.execute(tx -> deliveries.finishAttempt(delivery.getId(), token, now,
                WebhookDeliveryStatus.SECRET_UNAVAILABLE.name(), 0, delivery.getNextAttemptAt(), null,
                delivery.getLastOutcome() == null ? null : delivery.getLastOutcome().name(), delivery.getLastHttpStatus()));
        if (updated != null && updated == 1) {
            secretUnavailable.increment();
            log.error("Webhook secret unavailable, record parked: deliveryId={}, eventId={}, endpointId={}",
                    delivery.getId(), delivery.getEventId(), delivery.getEndpointId());
        }
    }

    static WebhookAttemptOutcome outcomeOf(WebhookCallResult result) {
        return switch (result.outcome()) {
            case RESPONDED -> result.httpStatus() >= 200 && result.httpStatus() < 300
                    ? WebhookAttemptOutcome.SUCCEEDED
                    : WebhookAttemptOutcome.HTTP_ERROR;
            case TIMEOUT -> WebhookAttemptOutcome.TIMEOUT;
            case NETWORK_ERROR -> WebhookAttemptOutcome.NETWORK_ERROR;
            case REFUSED_DESTINATION -> WebhookAttemptOutcome.REFUSED_DESTINATION;
        };
    }

    /** Retry: timeouts, network errors, 5xx, 408 and 429. Never 3xx, other 4xx or a refused destination. */
    static boolean isRetryable(WebhookCallResult result) {
        return switch (result.outcome()) {
            case TIMEOUT, NETWORK_ERROR -> true;
            case REFUSED_DESTINATION -> false;
            case RESPONDED -> {
                int status = result.httpStatus();
                yield status >= 500 || status == 408 || status == 429;
            }
        };
    }

    /** Backoff after {@code failedAttempts} attempts, with jitter, at least {@code Retry-After}, capped. */
    Duration nextDelay(int failedAttempts, String retryAfter, Instant now) {
        long factor = 1L << Math.min(failedAttempts - 1, 20);
        Duration delay = cap(properties.backoffBase().multipliedBy(factor));
        double spread = (random.nextDouble() * 2 - 1) * properties.jitter();
        delay = cap(Duration.ofMillis(Math.round(delay.toMillis() * (1 + spread))));
        Optional<Duration> requested = retryAfter(retryAfter, now);
        if (requested.isPresent() && requested.get().compareTo(delay) > 0) {
            delay = cap(requested.get());
        }
        return delay;
    }

    /**
     * Parses {@code Retry-After} as delay-seconds or an HTTP-date relative to {@code now}. Invalid values and
     * negative delays are ignored; the caller caps the result.
     */
    static Optional<Duration> retryAfter(String header, Instant now) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String value = header.trim();
        if (value.chars().allMatch(Character::isDigit)) {
            try {
                return Optional.of(Duration.ofSeconds(Long.parseLong(value)));
            } catch (NumberFormatException e) {
                // Too large for a long: longer than any cap
                return Optional.of(Duration.ofDays(365));
            }
        }
        try {
            Duration delay = Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant());
            return delay.isNegative() ? Optional.empty() : Optional.of(delay);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private Duration cap(Duration delay) {
        return delay.compareTo(properties.backoffCap()) > 0 ? properties.backoffCap() : delay;
    }
}
