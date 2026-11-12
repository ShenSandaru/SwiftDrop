package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.api.MerchantStatusChanged;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistory;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.web.MerchantApprovalRequest;
import com.swiftdrop.merchant.web.MerchantStatusResponse;

@ExtendWith(MockitoExtension.class)
class MerchantAdminServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantStatusHistoryRepository historyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MerchantAdminService service;

    private final UUID merchantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MerchantAdminService(merchantRepository, historyRepository, eventPublisher);
    }

    private Merchant pendingMerchant() {
        Merchant merchant = new Merchant(
                userId, "Test Store", "Retail", "BR-100", "+94771234567", "Address", null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(merchant, "id", merchantId);
        return merchant;
    }

    @Test
    @DisplayName("Admin approval transitions merchant to APPROVED and publishes event")
    void approveApplication() {
        Merchant merchant = pendingMerchant();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        MerchantApprovalRequest request = new MerchantApprovalRequest(
                MerchantApprovalRequest.Decision.APPROVE, null);

        MerchantStatusResponse response = service.reviewApplication(merchantId, request, adminId);

        assertThat(response.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(merchant.getDecidedBy()).isEqualTo(adminId);

        // Verify history saved
        ArgumentCaptor<MerchantStatusHistory> historyCaptor = ArgumentCaptor.forClass(MerchantStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(historyCaptor.getValue().getActorUserId()).isEqualTo(adminId);

        // Verify event published
        ArgumentCaptor<MerchantStatusChanged> eventCaptor = ArgumentCaptor.forClass(MerchantStatusChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().toStatus()).isEqualTo(MerchantStatus.APPROVED);
    }

    @Test
    @DisplayName("Admin rejection transitions merchant to REJECTED with reason and publishes event")
    void rejectApplication() {
        Merchant merchant = pendingMerchant();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        MerchantApprovalRequest request = new MerchantApprovalRequest(
                MerchantApprovalRequest.Decision.REJECT, "Registration document incomplete");

        MerchantStatusResponse response = service.reviewApplication(merchantId, request, adminId);

        assertThat(response.status()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(response.decisionReason()).isEqualTo("Registration document incomplete");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(merchant.getStatusReason()).isEqualTo("Registration document incomplete");

        // Verify event published
        ArgumentCaptor<MerchantStatusChanged> eventCaptor = ArgumentCaptor.forClass(MerchantStatusChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().toStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(eventCaptor.getValue().isRevocation()).isTrue();
    }

    @Test
    @DisplayName("Rejection without reason throws IllegalArgumentException")
    void rejectWithoutReason() {
        Merchant merchant = pendingMerchant();
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantApprovalRequest request = new MerchantApprovalRequest(
                MerchantApprovalRequest.Decision.REJECT, "   ");

        assertThatThrownBy(() -> service.reviewApplication(merchantId, request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejection reason is required");
    }
}
