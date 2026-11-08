package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelStatus;
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

@ExtendWith(MockitoExtension.class)
class MerchantWebhookServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantWebhookEndpointRepository endpointRepository;

    @Mock
    private WebhookUrlValidator urlValidator;

    private SecretCipher secretCipher;
    private MerchantWebhookService webhookService;

    private final UUID userId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private Merchant approvedMerchant;

    @BeforeEach
    void setUp() {
        secretCipher = new SecretCipher("master-encryption-key-for-test-32b!");
        webhookService = new MerchantWebhookService(merchantRepository, endpointRepository, urlValidator, secretCipher);

        approvedMerchant = new Merchant(
                userId, "Apex Store", "Retail", "BR-1001",
                "+94771234567", "No 10, Main St", "Apex Retailers Ltd", "https://apex.com");
        approvedMerchant.approve(UUID.randomUUID());
        ReflectionTestUtils.setField(approvedMerchant, "id", merchantId);
    }

    @Test
    @DisplayName("Create endpoint succeeds and returns raw secret once")
    void createEndpointSucceeds() {
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
        when(endpointRepository.countByMerchantIdAndDeletedAtIsNull(approvedMerchant.getId())).thenReturn(0L);
        when(urlValidator.validate("https://apex.com/hook")).thenReturn(WebhookUrlValidator.ValidationResult.valid());
        when(endpointRepository.save(any(MerchantWebhookEndpoint.class))).thenAnswer(inv -> {
            MerchantWebhookEndpoint ep = inv.getArgument(0);
            ReflectionTestUtils.setField(ep, "id", UUID.randomUUID());
            return ep;
        });

        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest(
                "https://apex.com/hook",
                "Apex order updates",
                Set.of(ParcelStatus.CREATED, ParcelStatus.DELIVERED)
        );

        CreateWebhookEndpointResponse response = webhookService.createEndpoint(userId, request);

        assertThat(response.url()).isEqualTo("https://apex.com/hook");
        assertThat(response.description()).isEqualTo("Apex order updates");
        assertThat(response.secret()).startsWith("whsec_");
        assertThat(response.secretHint()).isNotBlank();
        assertThat(response.events()).containsExactlyInAnyOrder(ParcelStatus.CREATED, ParcelStatus.DELIVERED);

        ArgumentCaptor<MerchantWebhookEndpoint> captor = ArgumentCaptor.forClass(MerchantWebhookEndpoint.class);
        verify(endpointRepository).save(captor.capture());
        MerchantWebhookEndpoint saved = captor.getValue();
        assertThat(saved.getSecretCiphertext()).isNotEqualTo(response.secret());
        assertThat(saved.getSecretIv()).isNotBlank();
    }

    @Test
    @DisplayName("Create endpoint fails when merchant reaches maximum limit of 5")
    void createEndpointLimitExceeded() {
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
        when(endpointRepository.countByMerchantIdAndDeletedAtIsNull(approvedMerchant.getId())).thenReturn(5L);

        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest(
                "https://apex.com/hook", "Desc", Set.of(ParcelStatus.CREATED));

        assertThatThrownBy(() -> webhookService.createEndpoint(userId, request))
                .isInstanceOf(WebhookEndpointLimitExceededException.class)
                .hasMessageContaining("limit reached (maximum 5 endpoints)");
    }

    @Test
    @DisplayName("Create endpoint fails if merchant is not approved")
    void createEndpointRequiresApprovedMerchant() {
        Merchant pendingMerchant = new Merchant(
                userId, "Pending Store", "Retail", "BR-1002",
                "+94771234568", "No 11, Main St", "Pending Ltd", null);
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(pendingMerchant));

        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest(
                "https://pending.com/hook", "Desc", Set.of(ParcelStatus.CREATED));

        assertThatThrownBy(() -> webhookService.createEndpoint(userId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Action requires an approved merchant account");
    }

    @Test
    @DisplayName("Create endpoint fails when URL validation fails")
    void createEndpointInvalidUrl() {
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
        when(endpointRepository.countByMerchantIdAndDeletedAtIsNull(approvedMerchant.getId())).thenReturn(0L);
        when(urlValidator.validate("http://insecure.com/hook"))
                .thenReturn(WebhookUrlValidator.ValidationResult.invalid("Webhook URL must use HTTPS"));

        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest(
                "http://insecure.com/hook", "Desc", Set.of(ParcelStatus.CREATED));

        assertThatThrownBy(() -> webhookService.createEndpoint(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook URL must use HTTPS");
    }

    @Test
    @DisplayName("List endpoints returns non-deleted endpoints")
    void listEndpoints() {
        MerchantWebhookEndpoint ep1 = new MerchantWebhookEndpoint(
                approvedMerchant.getId(), "https://apex.com/hook1", "Desc 1",
                "cipher1", "iv1", 1, "h1", Set.of(ParcelStatus.CREATED));
        ReflectionTestUtils.setField(ep1, "id", UUID.randomUUID());
        MerchantWebhookEndpoint ep2 = new MerchantWebhookEndpoint(
                approvedMerchant.getId(), "https://apex.com/hook2", "Desc 2",
                "cipher2", "iv2", 1, "h2", Set.of(ParcelStatus.DELIVERED));
        ReflectionTestUtils.setField(ep2, "id", UUID.randomUUID());

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
        when(endpointRepository.findByMerchantIdAndDeletedAtIsNull(approvedMerchant.getId())).thenReturn(List.of(ep1, ep2));

        List<WebhookEndpointResponse> list = webhookService.listEndpoints(userId);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).url()).isEqualTo("https://apex.com/hook1");
        assertThat(list.get(1).url()).isEqualTo("https://apex.com/hook2");
    }

    @Test
    @DisplayName("Get endpoint returns endpoint or throws WebhookEndpointNotFoundException")
    void getEndpoint() {
        UUID epId = UUID.randomUUID();
        MerchantWebhookEndpoint ep = new MerchantWebhookEndpoint(
                approvedMerchant.getId(), "https://apex.com/hook", "Desc",
                "cipher", "iv", 1, "hint", Set.of(ParcelStatus.CREATED));
        ReflectionTestUtils.setField(ep, "id", epId);

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
        when(endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(epId, approvedMerchant.getId()))
                .thenReturn(Optional.of(ep));

        WebhookEndpointResponse response = webhookService.getEndpoint(userId, epId);
        assertThat(response.url()).isEqualTo("https://apex.com/hook");

        UUID missingId = UUID.randomUUID();
        when(endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(missingId, approvedMerchant.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.getEndpoint(userId, missingId))
                .isInstanceOf(WebhookEndpointNotFoundException.class);
    }

    @Test
    @DisplayName("Update endpoint updates fields")
    void updateEndpoint() {
        UUID epId = UUID.randomUUID();
        MerchantWebhookEndpoint ep = new MerchantWebhookEndpoint(
                approvedMerchant.getId(), "https://apex.com/old", "Old",
                "cipher", "iv", 1, "hint", Set.of(ParcelStatus.CREATED));
        ReflectionTestUtils.setField(ep, "id", epId);

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
        when(endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(epId, approvedMerchant.getId()))
                .thenReturn(Optional.of(ep));
        when(urlValidator.validate("https://apex.com/new")).thenReturn(WebhookUrlValidator.ValidationResult.valid());
        when(endpointRepository.save(ep)).thenReturn(ep);

        UpdateWebhookEndpointRequest request = new UpdateWebhookEndpointRequest(
                "https://apex.com/new", "New desc", false, Set.of(ParcelStatus.DELIVERED));

        WebhookEndpointResponse response = webhookService.updateEndpoint(userId, epId, request);

        assertThat(response.url()).isEqualTo("https://apex.com/new");
        assertThat(response.description()).isEqualTo("New desc");
        assertThat(response.enabled()).isFalse();
        assertThat(response.events()).containsExactly(ParcelStatus.DELIVERED);
    }

    @Test
    @DisplayName("Soft-delete marks endpoint as deleted")
    void deleteEndpoint() {
        UUID epId = UUID.randomUUID();
        MerchantWebhookEndpoint ep = new MerchantWebhookEndpoint(
                approvedMerchant.getId(), "https://apex.com/hook", "Desc",
                "cipher", "iv", 1, "hint", Set.of(ParcelStatus.CREATED));
        ReflectionTestUtils.setField(ep, "id", epId);

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
        when(endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(epId, approvedMerchant.getId()))
                .thenReturn(Optional.of(ep));

        webhookService.deleteEndpoint(userId, epId);

        assertThat(ep.isDeleted()).isTrue();
        verify(endpointRepository).save(ep);
    }

    @Test
    @DisplayName("Rotate secret generates new secret and updates endpoint")
    void rotateSecret() {
        UUID epId = UUID.randomUUID();
        MerchantWebhookEndpoint ep = new MerchantWebhookEndpoint(
                approvedMerchant.getId(), "https://apex.com/hook", "Desc",
                "cipher", "iv", 1, "oldh", Set.of(ParcelStatus.CREATED));
        ReflectionTestUtils.setField(ep, "id", epId);

        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(approvedMerchant));
        when(endpointRepository.findByIdAndMerchantIdAndDeletedAtIsNull(epId, approvedMerchant.getId()))
                .thenReturn(Optional.of(ep));
        when(endpointRepository.save(ep)).thenReturn(ep);

        RotateSecretResponse response = webhookService.rotateSecret(userId, epId);

        assertThat(response.secret()).startsWith("whsec_");
        assertThat(response.secretHint()).isNotBlank();
        assertThat(ep.getSecretHint()).isEqualTo(response.secretHint());
        verify(endpointRepository).save(ep);
    }
}

