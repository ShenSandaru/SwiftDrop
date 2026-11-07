package com.swiftdrop.merchant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.ParcelStatus;

class MerchantWebhookEndpointTest {

    private final UUID merchantId = UUID.randomUUID();

    @Test
    @DisplayName("New webhook endpoint is enabled by default with encrypted secret and subscribed events")
    void newWebhookEndpointInitialState() {
        MerchantWebhookEndpoint endpoint = new MerchantWebhookEndpoint(
                merchantId,
                "https://api.merchant.com/webhook",
                "Order status updates",
                "ciphertext123",
                "iv123",
                1,
                "3a9f",
                Set.of(ParcelStatus.CREATED, ParcelStatus.DELIVERED));

        assertThat(endpoint.getId()).isNull();
        assertThat(endpoint.getMerchantId()).isEqualTo(merchantId);
        assertThat(endpoint.getUrl()).isEqualTo("https://api.merchant.com/webhook");
        assertThat(endpoint.getDescription()).isEqualTo("Order status updates");
        assertThat(endpoint.isEnabled()).isTrue();
        assertThat(endpoint.isDeleted()).isFalse();
        assertThat(endpoint.getDeletedAt()).isNull();
        assertThat(endpoint.getSecretCiphertext()).isEqualTo("ciphertext123");
        assertThat(endpoint.getSecretIv()).isEqualTo("iv123");
        assertThat(endpoint.getSecretKeyVersion()).isEqualTo(1);
        assertThat(endpoint.getSecretHint()).isEqualTo("3a9f");
        assertThat(endpoint.getEvents()).containsExactlyInAnyOrder(ParcelStatus.CREATED, ParcelStatus.DELIVERED);
        assertThat(endpoint.getCreatedAt()).isNotNull();
        assertThat(endpoint.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Enable and disable update the enabled flag and updatedAt")
    void enableDisableToggle() {
        MerchantWebhookEndpoint endpoint = new MerchantWebhookEndpoint(
                merchantId, "https://api.merchant.com/webhook", null,
                "cipher", "iv", 1, "hint", Set.of(ParcelStatus.CREATED));

        endpoint.disable();
        assertThat(endpoint.isEnabled()).isFalse();

        endpoint.enable();
        assertThat(endpoint.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Soft delete marks deletedAt and disables endpoint")
    void softDelete() {
        MerchantWebhookEndpoint endpoint = new MerchantWebhookEndpoint(
                merchantId, "https://api.merchant.com/webhook", null,
                "cipher", "iv", 1, "hint", Set.of(ParcelStatus.CREATED));

        endpoint.softDelete();
        assertThat(endpoint.isDeleted()).isTrue();
        assertThat(endpoint.getDeletedAt()).isNotNull();
        assertThat(endpoint.isEnabled()).isFalse();

        // Mutating a soft-deleted endpoint throws IllegalStateException
        assertThatThrownBy(endpoint::enable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> endpoint.update("https://new.url", "desc", Set.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> endpoint.rotateSecret("c2", "iv2", 2, "hint2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Update modifies URL, description, and event subscriptions")
    void updateEndpoint() {
        MerchantWebhookEndpoint endpoint = new MerchantWebhookEndpoint(
                merchantId, "https://api.merchant.com/webhook", "Old desc",
                "cipher", "iv", 1, "hint", Set.of(ParcelStatus.CREATED));

        endpoint.update("https://new.merchant.com/hook", "New desc",
                Set.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED));

        assertThat(endpoint.getUrl()).isEqualTo("https://new.merchant.com/hook");
        assertThat(endpoint.getDescription()).isEqualTo("New desc");
        assertThat(endpoint.getEvents()).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
    }

    @Test
    @DisplayName("Rotate secret updates ciphertext, iv, key version and hint")
    void rotateSecret() {
        MerchantWebhookEndpoint endpoint = new MerchantWebhookEndpoint(
                merchantId, "https://api.merchant.com/webhook", null,
                "cipher1", "iv1", 1, "hint1", Set.of(ParcelStatus.CREATED));

        endpoint.rotateSecret("cipher2", "iv2", 2, "hint2");

        assertThat(endpoint.getSecretCiphertext()).isEqualTo("cipher2");
        assertThat(endpoint.getSecretIv()).isEqualTo("iv2");
        assertThat(endpoint.getSecretKeyVersion()).isEqualTo(2);
        assertThat(endpoint.getSecretHint()).isEqualTo("hint2");
    }
}

