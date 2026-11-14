package com.swiftdrop.merchant.application;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.api.WebhookUrlValidator;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.domain.WebhookEndpointLimitExceededException;
import com.swiftdrop.merchant.domain.WebhookEndpointNotFoundException;
import com.swiftdrop.merchant.security.SecretCipher;
import com.swiftdrop.merchant.web.CreateWebhookEndpointRequest;
import com.swiftdrop.merchant.web.CreateWebhookEndpointResponse;
import com.swiftdrop.merchant.web.RotateSecretResponse;
import com.swiftdrop.merchant.web.UpdateWebhookEndpointRequest;
import com.swiftdrop.merchant.web.WebhookEndpointResponse;

/**
 * Application service managing merchant webhook endpoint subscriptions (Phase 4).
 * <p>
 * Handles endpoint creation, URL syntax feedback, AES-GCM secret encryption, secret rotation,
 * and soft-deletion. Enforces maximum per-merchant subscription caps and checks merchant approval.
 * <p>
 * <b>Security & Privacy (NFR-04):</b>
 * Never logs raw signing secrets, request payloads, or full URLs with query parameters.
 * Configuration-time URL checks are solely for immediate merchant UX validation; Member B performs
 * real SSRF and DNS-rebinding defense at delivery time.
 */
@Service
@Transactional
public class MerchantWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookService.class);
    public static final int MAX_ENDPOINTS_PER_MERCHANT = 5;
    private static final String SECRET_PREFIX = "whsec_";
    private static final int RAW_SECRET_BYTES = 32;

    private final MerchantRepository merchantRepository;
    private final MerchantWebhookEndpointRepository endpointRepository;
    private final WebhookUrlValidator urlValidator;
    private final SecretCipher secretCipher;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantWebhookService(
            MerchantRepository merchantRepository,
            MerchantWebhookEndpointRepository endpointRepository,
            WebhookUrlValidator urlValidator,
            SecretCipher secretCipher) {
        this.merchantRepository = Objects.requireNonNull(merchantRepository, "merchantRepository must not be null");
        this.endpointRepository = Objects.requireNonNull(endpointRepository, "endpointRepository must not be null");
        this.urlValidator = Objects.requireNonNull(urlValidator, "urlValidator must not be null");
        this.secretCipher = Objects.requireNonNull(secretCipher, "secretCipher must not be null");
    }

    /**
     * Creates a new webhook endpoint subscription for an approved merchant.
     * Generates a random 32-byte signing secret and returns it raw once.
     */
    public CreateWebhookEndpointResponse createEndpoint(UUID userId, CreateWebhookEndpointRequest request) {
        Merchant merchant = getApprovedMerchantForUser(userId);

        long activeCount = endpointRepository.countByMerchantIdAndDeletedAtIsNull(merchant.getId());
        if (activeCount >= MAX_ENDPOINTS_PER_MERCHANT) {
            throw new WebhookEndpointLimitExceededException(MAX_ENDPOINTS_PER_MERCHANT);
        }

        validateUrl(request.url());

        String rawSecret = generateSigningSecret();
        SecretCipher.EncryptedSecret encrypted = secretCipher.encrypt(rawSecret);

        MerchantWebhookEndpoint endpoint = new MerchantWebhookEndpoint(
                merchant.getId(),
                request.url().trim(),
                request.description(),
                encrypted.ciphertext(),
                encrypted.iv(),
                encrypted.keyVersion(),
                encrypted.hint(),
                request.events()
        );

        MerchantWebhookEndpoint saved = endpointRepository.save(endpoint);
        log.info("Created webhook endpoint {} for merchant {}", saved.getId(), merchant.getId());

        return CreateWebhookEndpointResponse.of(saved, rawSecret);
    }

    /**
     * Lists active (non-deleted) webhook endpoints for the authenticated merchant.
     */
    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> listEndpoints(UUID userId) {
        Merchant merchant = getMerchantForUser(userId);
        return endpointRepository.findByMerchantIdAndDeletedAtIsNull(merchant.getId()).stream()
                .map(WebhookEndpointResponse::from)
                .toList();
    }

    /**
     * Gets a single active webhook endpoint by ID for the authenticated merchant.
     */
    @Transactional(readOnly = true)
    public WebhookEndpointResponse getEndpoint(UUID userId, UUID endpointId) {
        Merchant merchant = getMerchantForUser(userId);
        MerchantWebhookEndpoint endpoint = findActiveEndpoint(endpointId, merchant.getId());
        return WebhookEndpointResponse.from(endpoint);
    }

    /**
     * Updates an existing webhook endpoint's URL, description, enabled flag, and events.
     */
    public WebhookEndpointResponse updateEndpoint(UUID userId, UUID endpointId, UpdateWebhookEndpointRequest request) {
        Merchant merchant = getApprovedMerchantForUser(userId);
        MerchantWebhookEndpoint endpoint = findActiveEndpoint(endpointId, merchant.getId());

        if (request.url() != null && !request.url().isBlank()) {
            validateUrl(request.url());
        }

        endpoint.update(request.url(), request.description(), request.enabled(), request.events());
        MerchantWebhookEndpoint updated = endpointRepository.save(endpoint);
        log.info("Updated webhook endpoint {} for merchant {}", updated.getId(), merchant.getId());

        return WebhookEndpointResponse.from(updated);
    }

    /**
     * Soft-deletes a webhook endpoint.
     */
    public void deleteEndpoint(UUID userId, UUID endpointId) {
        Merchant merchant = getApprovedMerchantForUser(userId);
        MerchantWebhookEndpoint endpoint = findActiveEndpoint(endpointId, merchant.getId());
        endpoint.softDelete();
        endpointRepository.save(endpoint);
        log.info("Soft-deleted webhook endpoint {} for merchant {}", endpointId, merchant.getId());
    }

    /**
     * Rotates the signing secret of an existing webhook endpoint.
     * Generates a new 32-byte secret, encrypts it, and returns the new raw secret once.
     */
    public RotateSecretResponse rotateSecret(UUID userId, UUID endpointId) {
        Merchant merchant = getApprovedMerchantForUser(userId);
        MerchantWebhookEndpoint endpoint = findActiveEndpoint(endpointId, merchant.getId());

        String newRawSecret = generateSigningSecret();
        SecretCipher.EncryptedSecret encrypted = secretCipher.encrypt(newRawSecret);

        endpoint.rotateSecret(encrypted.ciphertext(), encrypted.iv(), encrypted.keyVersion(), encrypted.hint());
        endpointRepository.save(endpoint);
        log.info("Rotated secret for webhook endpoint {} of merchant {}", endpointId, merchant.getId());

        return new RotateSecretResponse(endpoint.getId(), newRawSecret, encrypted.hint());
    }

    private Merchant getMerchantForUser(UUID userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Merchant profile not found for user"));
    }

    private Merchant getApprovedMerchantForUser(UUID userId) {
        Merchant merchant = getMerchantForUser(userId);
        if (merchant.getStatus() != MerchantStatus.APPROVED) {
            throw new AccessDeniedException(
                    String.format("Action requires an approved merchant account (current status: %s)", merchant.getStatus()));
        }
        return merchant;
    }

    private MerchantWebhookEndpoint findActiveEndpoint(UUID endpointId, UUID merchantId) {
        return endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(endpointId, merchantId)
                .orElseThrow(() -> new WebhookEndpointNotFoundException(endpointId));
    }

    private void validateUrl(String url) {
        WebhookUrlValidator.ValidationResult result = urlValidator.validate(url);
        if (!result.isValid()) {
            throw new IllegalArgumentException(result.message());
        }
    }

    private String generateSigningSecret() {
        byte[] bytes = new byte[RAW_SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
