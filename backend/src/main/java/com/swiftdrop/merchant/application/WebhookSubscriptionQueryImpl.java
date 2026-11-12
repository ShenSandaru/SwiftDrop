package com.swiftdrop.merchant.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.api.WebhookSubscriptionQuery;
import com.swiftdrop.merchant.api.WebhookSubscriptionView;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.security.SecretCipher;

/**
 * Implementation of {@link WebhookSubscriptionQuery} consumed by Member B (Phase 4, docs/merchant-contracts.md).
 * <p>
 * Resolves active, non-deleted, enabled webhook endpoints subscribed to a parcel status change event,
 * decrypts each endpoint's AES-GCM signing secret, and constructs the read-only subscription view.
 */
@Service
@Transactional(readOnly = true)
public class WebhookSubscriptionQueryImpl implements WebhookSubscriptionQuery {

    private final MerchantWebhookEndpointRepository endpointRepository;
    private final SecretCipher secretCipher;

    public WebhookSubscriptionQueryImpl(MerchantWebhookEndpointRepository endpointRepository, SecretCipher secretCipher) {
        this.endpointRepository = Objects.requireNonNull(endpointRepository, "endpointRepository must not be null");
        this.secretCipher = Objects.requireNonNull(secretCipher, "secretCipher must not be null");
    }

    @Override
    public List<WebhookSubscriptionView> findActiveFor(UUID merchantId, ParcelStatus eventType) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");

        List<MerchantWebhookEndpoint> endpoints = endpointRepository.findActiveForMerchantAndEvent(merchantId, eventType);

        return endpoints.stream()
                .map(this::toSubscriptionView)
                .toList();
    }

    private WebhookSubscriptionView toSubscriptionView(MerchantWebhookEndpoint endpoint) {
        String decryptedSecret = secretCipher.decrypt(
                endpoint.getSecretCiphertext(),
                endpoint.getSecretIv(),
                endpoint.getSecretKeyVersion()
        );

        return new WebhookSubscriptionView(
                endpoint.getId(),
                endpoint.getMerchantId(),
                endpoint.getUrl(),
                endpoint.getEvents(),
                decryptedSecret
        );
    }
}

