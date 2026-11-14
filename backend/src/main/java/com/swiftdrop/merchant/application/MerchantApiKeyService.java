package com.swiftdrop.merchant.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;

/**
 * Merchant users manage their own API keys (SD-403, ADR 0008 §6). Only an {@code APPROVED} merchant
 * may create, list or revoke keys; the merchant is always derived from the logged-in user.
 */
@Service
public class MerchantApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(MerchantApiKeyService.class);

    static final int MAX_ACTIVE_KEYS = 5;
    static final int MAX_PREFIX_ATTEMPTS = 5;

    private final MerchantRepository merchants;
    private final MerchantApiKeyRepository apiKeys;
    private final ApiKeyGenerator generator;
    private final Clock clock;

    @Autowired
    public MerchantApiKeyService(MerchantRepository merchants, MerchantApiKeyRepository apiKeys,
            ApiKeyGenerator generator) {
        this(merchants, apiKeys, generator, Clock.systemUTC());
    }

    MerchantApiKeyService(MerchantRepository merchants, MerchantApiKeyRepository apiKeys, ApiKeyGenerator generator,
            Clock clock) {
        this.merchants = merchants;
        this.apiKeys = apiKeys;
        this.generator = generator;
        this.clock = clock;
    }

    /** A newly created key; {@code apiKey} is the full key, returned only this once. */
    public record IssuedApiKey(MerchantApiKey key, String apiKey) {

        /** Never includes the full key. */
        @Override
        public String toString() {
            return "IssuedApiKey[" + key + "]";
        }
    }

    @Transactional
    public IssuedApiKey create(UUID userId, String label) {
        Merchant merchant = approvedMerchantOf(userId);
        if (apiKeys.countByMerchantIdAndRevokedAtIsNull(merchant.getId()) >= MAX_ACTIVE_KEYS) {
            throw new ApiKeyLimitReachedException(MAX_ACTIVE_KEYS);
        }
        String fullKey = generateUniqueKey();
        MerchantApiKey key = apiKeys.save(new MerchantApiKey(merchant.getId(), MerchantApiKeyFormat.prefixOf(fullKey),
                MerchantApiKeyFormat.hash(fullKey), label, userId, clock.instant()));
        log.info("Merchant API key created: merchantId={}, keyPrefix={}, userId={}",
                merchant.getId(), key.getKeyPrefix(), userId);
        return new IssuedApiKey(key, fullKey);
    }

    @Transactional(readOnly = true)
    public List<MerchantApiKey> list(UUID userId) {
        return apiKeys.findByMerchantIdOrderByCreatedAtDesc(approvedMerchantOf(userId).getId());
    }

    @Transactional
    public void revoke(UUID userId, UUID keyId) {
        Merchant merchant = approvedMerchantOf(userId);
        MerchantApiKey key = apiKeys.findByIdAndMerchantId(keyId, merchant.getId())
                .orElseThrow(ApiKeyNotFoundException::new);
        if (key.isActive()) {
            key.revoke(clock.instant());
            log.info("Merchant API key revoked: merchantId={}, keyPrefix={}, userId={}",
                    merchant.getId(), key.getKeyPrefix(), userId);
        }
    }

    private Merchant approvedMerchantOf(UUID userId) {
        return merchants.findByUserId(userId)
                .filter(Merchant::isApproved)
                .orElseThrow(() -> new AccessDeniedException("Merchant account is not approved"));
    }

    private String generateUniqueKey() {
        for (int attempt = 0; attempt < MAX_PREFIX_ATTEMPTS; attempt++) {
            String candidate = generator.generate();
            if (!apiKeys.existsByKeyPrefix(MerchantApiKeyFormat.prefixOf(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique API key prefix");
    }
}
