package com.swiftdrop.merchant.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.api.AuthenticatedMerchant;
import com.swiftdrop.merchant.api.MerchantAuthentication;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;

/**
 * Verifies merchant API keys (SD-404, ADR 0008 §3): format, lookup by prefix, constant-time hash
 * comparison, not revoked, merchant {@code APPROVED}. Nothing is cached, so revocation and suspension
 * apply to the next request.
 */
@Service
public class MerchantApiKeyAuthenticator implements MerchantAuthentication {

    static final Duration LAST_USED_INTERVAL = Duration.ofMinutes(1);

    private final MerchantApiKeyRepository apiKeys;
    private final MerchantRepository merchants;
    private final Clock clock;

    @Autowired
    public MerchantApiKeyAuthenticator(MerchantApiKeyRepository apiKeys, MerchantRepository merchants) {
        this(apiKeys, merchants, Clock.systemUTC());
    }

    MerchantApiKeyAuthenticator(MerchantApiKeyRepository apiKeys, MerchantRepository merchants, Clock clock) {
        this.apiKeys = apiKeys;
        this.merchants = merchants;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<AuthenticatedMerchant> authenticate(String apiKey) {
        if (!MerchantApiKeyFormat.isWellFormed(apiKey)) {
            return Optional.empty();
        }
        Optional<MerchantApiKey> stored = apiKeys.findByKeyPrefix(MerchantApiKeyFormat.prefixOf(apiKey));
        if (stored.isEmpty() || !MerchantApiKeyFormat.matches(apiKey, stored.get().getKeyHash())) {
            return Optional.empty();
        }
        MerchantApiKey key = stored.get();
        boolean approved = merchants.findById(key.getMerchantId()).map(Merchant::isApproved).orElse(false);
        if (!key.isActive() || !approved) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        apiKeys.recordUse(key.getId(), now, now.minus(LAST_USED_INTERVAL));
        return Optional.of(new AuthenticatedMerchant(key.getMerchantId(), key.getId(), key.getKeyPrefix()));
    }
}
