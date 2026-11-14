package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantApiKey;
import com.swiftdrop.merchant.domain.MerchantApiKeyFormat;
import com.swiftdrop.merchant.domain.MerchantApiKeyRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;

class MerchantApiKeyServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MERCHANT_ID = UUID.randomUUID();

    private MerchantRepository merchants;
    private MerchantApiKeyRepository apiKeys;
    private ApiKeyGenerator generator;
    private MerchantApiKeyService service;

    @BeforeEach
    void setUp() {
        merchants = mock(MerchantRepository.class);
        apiKeys = mock(MerchantApiKeyRepository.class);
        generator = mock(ApiKeyGenerator.class);
        service = new MerchantApiKeyService(merchants, apiKeys, generator, Clock.fixed(NOW, ZoneOffset.UTC));
        when(apiKeys.save(any(MerchantApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Create stores only the prefix and hash and returns the full key once")
    void create() {
        givenMerchant(true);
        String fullKey = key("abcdefgh1234");
        when(generator.generate()).thenReturn(fullKey);

        var issued = service.create(USER_ID, " Production ");

        assertThat(issued.apiKey()).isEqualTo(fullKey);
        MerchantApiKey stored = issued.key();
        assertThat(stored.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(stored.getKeyPrefix()).isEqualTo("abcdefgh1234");
        assertThat(stored.getKeyHash()).isEqualTo(MerchantApiKeyFormat.hash(fullKey)).doesNotContain(fullKey);
        assertThat(stored.getLabel()).isEqualTo("Production");
        assertThat(stored.getCreatedByUserId()).isEqualTo(USER_ID);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(issued.toString()).doesNotContain(fullKey);
    }

    @Test
    @DisplayName("A prefix collision is retried with a new key")
    void prefixCollisionRetried() {
        givenMerchant(true);
        when(generator.generate()).thenReturn(key("taken0000000"), key("free00000000"));
        when(apiKeys.existsByKeyPrefix("taken0000000")).thenReturn(true);

        assertThat(service.create(USER_ID, "Live").key().getKeyPrefix()).isEqualTo("free00000000");
    }

    @Test
    @DisplayName("Generation gives up after repeated prefix collisions")
    void prefixCollisionsExhausted() {
        givenMerchant(true);
        when(generator.generate()).thenReturn(key("taken0000000"));
        when(apiKeys.existsByKeyPrefix(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER_ID, "Live")).isInstanceOf(IllegalStateException.class);
        verify(apiKeys, never()).save(any());
    }

    @Test
    @DisplayName("A merchant with five active keys cannot create another")
    void activeKeyLimit() {
        givenMerchant(true);
        when(apiKeys.countByMerchantIdAndRevokedAtIsNull(MERCHANT_ID)).thenReturn(5L);

        assertThatThrownBy(() -> service.create(USER_ID, "Sixth")).isInstanceOf(ApiKeyLimitReachedException.class);
        verify(generator, never()).generate();
    }

    @Test
    @DisplayName("Merchants that are not approved, and users without a merchant, cannot manage keys")
    void notApproved() {
        givenMerchant(false);
        assertThatThrownBy(() -> service.create(USER_ID, "Live")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.list(USER_ID)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.revoke(USER_ID, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);

        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(USER_ID, "Live")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Blank or over-long labels are rejected")
    void labels() {
        givenMerchant(true);
        when(generator.generate()).thenReturn(key("abcdefgh1234"));

        assertThatThrownBy(() -> service.create(USER_ID, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(USER_ID, "x".repeat(101))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("List returns the merchant's keys")
    void list() {
        givenMerchant(true);
        MerchantApiKey key = storedKey();
        when(apiKeys.findByMerchantIdOrderByCreatedAtDesc(MERCHANT_ID)).thenReturn(List.of(key));

        assertThat(service.list(USER_ID)).containsExactly(key);
    }

    @Test
    @DisplayName("Revoke sets the revocation time once; repeating it keeps the first time")
    void revoke() {
        givenMerchant(true);
        MerchantApiKey key = storedKey();
        when(apiKeys.findByIdAndMerchantId(key.getId(), MERCHANT_ID)).thenReturn(Optional.of(key));

        service.revoke(USER_ID, key.getId());
        assertThat(key.getRevokedAt()).isEqualTo(NOW);
        assertThat(key.isActive()).isFalse();

        key.revoke(NOW.plusSeconds(60));
        assertThat(key.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Revoking another merchant's or an unknown key is not found")
    void revokeOtherMerchantsKey() {
        givenMerchant(true);
        UUID otherKey = UUID.randomUUID();
        when(apiKeys.findByIdAndMerchantId(otherKey, MERCHANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(USER_ID, otherKey)).isInstanceOf(ApiKeyNotFoundException.class);
    }

    private void givenMerchant(boolean approved) {
        Merchant merchant = new Merchant(USER_ID, "Shop", "Retail", "REG-1", "+94771234567", "Main St", null, null);
        ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
        if (approved) {
            merchant.approve(UUID.randomUUID());
        }
        when(merchants.findByUserId(USER_ID)).thenReturn(Optional.of(merchant));
    }

    private static MerchantApiKey storedKey() {
        String fullKey = key("abcdefgh1234");
        MerchantApiKey key = new MerchantApiKey(MERCHANT_ID, "abcdefgh1234", MerchantApiKeyFormat.hash(fullKey),
                "Live", USER_ID, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(key, "id", UUID.randomUUID());
        return key;
    }

    static String key(String prefix) {
        return "sdk_" + prefix + "_" + "S".repeat(43);
    }
}
