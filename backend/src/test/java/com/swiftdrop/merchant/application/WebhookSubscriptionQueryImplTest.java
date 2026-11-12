package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.api.WebhookSubscriptionView;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.merchant.security.SecretCipher;

@ExtendWith(MockitoExtension.class)
class WebhookSubscriptionQueryImplTest {

    @Mock
    private MerchantWebhookEndpointRepository endpointRepository;

    private SecretCipher secretCipher;
    private WebhookSubscriptionQueryImpl query;

    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        secretCipher = new SecretCipher("master-encryption-key-for-test-32b!");
        query = new WebhookSubscriptionQueryImpl(endpointRepository, secretCipher);
    }

    @Test
    @DisplayName("findActiveFor decrypts secret and returns active subscriptions for Member B")
    void findActiveForDecryptsSecret() {
        String rawSecret = "whsec_raw_signing_secret_1234567890";
        SecretCipher.EncryptedSecret encrypted = secretCipher.encrypt(rawSecret);

        MerchantWebhookEndpoint endpoint = new MerchantWebhookEndpoint(
                merchantId,
                "https://merchant.example.com/events",
                "Prod hook",
                encrypted.ciphertext(),
                encrypted.iv(),
                encrypted.keyVersion(),
                encrypted.hint(),
                Set.of(ParcelStatus.IN_TRANSIT, ParcelStatus.DELIVERED)
        );
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());

        when(endpointRepository.findActiveForMerchantAndEvent(merchantId, ParcelStatus.IN_TRANSIT))
                .thenReturn(List.of(endpoint));

        List<WebhookSubscriptionView> views = query.findActiveFor(merchantId, ParcelStatus.IN_TRANSIT);

        assertThat(views).hasSize(1);
        WebhookSubscriptionView view = views.get(0);
        assertThat(view.merchantId()).isEqualTo(merchantId);
        assertThat(view.url()).isEqualTo("https://merchant.example.com/events");
        assertThat(view.eventTypes()).containsExactlyInAnyOrder(ParcelStatus.IN_TRANSIT, ParcelStatus.DELIVERED);
        assertThat(view.signingSecret()).isEqualTo(rawSecret);
    }
}

