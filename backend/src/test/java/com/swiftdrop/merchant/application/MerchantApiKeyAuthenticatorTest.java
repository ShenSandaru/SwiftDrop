package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.merchant.api.AuthenticatedMerchant;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;

class MerchantApiKeyAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final UUID MERCHANT_ID = UUID.randomUUID();
    private static final String PREFIX = "abcdefgh1234";
    private static final String KEY = MerchantApiKeyServiceTest.key(PREFIX);

    private MerchantApiKeyRepository apiKeys;
    private MerchantRepository merchants;
    private MerchantApiKeyAuthenticator authenticator;
    private MerchantApiKey stored;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        apiKeys = mock(MerchantApiKeyRepository.class);
        merchants = mock(MerchantRepository.class);
        authenticator = new MerchantApiKeyAuthenticator(apiKeys, merchants, Clock.fixed(NOW, ZoneOffset.UTC));

        stored = new MerchantApiKey(MERCHANT_ID, PREFIX, MerchantApiKeyFormat.hash(KEY), "Live", UUID.randomUUID(),
                NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(stored, "id", UUID.randomUUID());
        merchant = new Merchant(UUID.randomUUID(), "Shop", "Retail", "REG-1", "+94771234567", "Main St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        merchant.approve(UUID.randomUUID());

        when(apiKeys.findByKeyPrefix(PREFIX)).thenReturn(Optional.of(stored));
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
    }

    @Test
    @DisplayName("A valid key of an approved merchant authenticates as that merchant and records the use")
    void valid() {
        Optional<AuthenticatedMerchant> result = authenticator.authenticate(KEY);

        assertThat(result).contains(new AuthenticatedMerchant(MERCHANT_ID, stored.getId(), PREFIX));
        verify(apiKeys).recordUse(stored.getId(), NOW, NOW.minusSeconds(60));
    }

    @Test
    @DisplayName("Malformed keys fail without touching the database")
    void malformed() {
        assertThat(authenticator.authenticate(null)).isEmpty();
        assertThat(authenticator.authenticate("sdk_short")).isEmpty();
        assertThat(authenticator.authenticate("Bearer " + KEY)).isEmpty();
        verify(apiKeys, never()).findByKeyPrefix(anyString());
    }

    @Test
    @DisplayName("An unknown prefix fails")
    void unknownPrefix() {
        assertThat(authenticator.authenticate(MerchantApiKeyServiceTest.key("zzzzzzzzzzzz"))).isEmpty();
    }

    @Test
    @DisplayName("A wrong secret with a known prefix fails")
    void wrongSecret() {
        String wrong = "sdk_" + PREFIX + "_" + "T".repeat(43);
        assertThat(authenticator.authenticate(wrong)).isEmpty();
        verify(apiKeys, never()).recordUse(any(), any(), any());
    }

    @Test
    @DisplayName("A revoked key fails")
    void revoked() {
        stored.revoke(NOW.minusSeconds(1));
        assertThat(authenticator.authenticate(KEY)).isEmpty();
        verify(apiKeys, never()).recordUse(any(), any(), any());
    }

    @Test
    @DisplayName("A merchant that is pending, rejected, suspended or missing fails")
    void merchantNotApproved() {
        merchant.suspend(UUID.randomUUID(), "Chargebacks");
        assertThat(authenticator.authenticate(KEY)).isEmpty();

        Merchant pending = new Merchant(UUID.randomUUID(), "Shop", "Retail", "REG-2", "+94771234567", "St", null, null);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(pending));
        assertThat(authenticator.authenticate(KEY)).isEmpty();

        pending.reject(UUID.randomUUID(), "Incomplete");
        assertThat(authenticator.authenticate(KEY)).isEmpty();

        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.empty());
        assertThat(authenticator.authenticate(KEY)).isEmpty();
        verify(apiKeys, never()).recordUse(any(), any(), any());
    }
}
