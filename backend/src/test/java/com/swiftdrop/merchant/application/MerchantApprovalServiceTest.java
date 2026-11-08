package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.api.MerchantStatusChanged;
import com.swiftdrop.merchant.domain.IllegalStateTransitionException;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistory;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.web.ApprovalDecisionRequest;
import com.swiftdrop.merchant.web.MerchantDetailResponse;
import com.swiftdrop.merchant.web.MerchantProfileUpdateRequest;
import com.swiftdrop.merchant.web.MerchantStatusResponse;

@ExtendWith(MockitoExtension.class)
class MerchantApprovalServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantStatusHistoryRepository historyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MerchantApprovalService approvalService;

    private final UUID merchantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        approvalService = new MerchantApprovalService(merchantRepository, historyRepository, eventPublisher);
    }

    private Merchant createSampleMerchant(MerchantStatus initialStatus) {
        Merchant merchant = new Merchant(
                userId,
                "Acme Store",
                "Retail",
                "BR-12345",
                "+94771234567",
                "123 Galle Road, Colombo",
                "Supermarket goods",
                "https://acme.example.com");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        if (initialStatus == MerchantStatus.APPROVED) {
            merchant.approve(adminId);
        } else if (initialStatus == MerchantStatus.REJECTED) {
            merchant.reject(adminId, "Invalid documents");
        } else if (initialStatus == MerchantStatus.SUSPENDED) {
            merchant.approve(adminId);
            merchant.suspend(adminId, "Fraud investigation");
        }
        return merchant;
    }

    @Test
    @DisplayName("Admin decision APPROVE transitions PENDING merchant to APPROVED, logs history and publishes event")
    void approvePendingMerchant() {
        Merchant merchant = createSampleMerchant(MerchantStatus.PENDING);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalDecisionRequest request = new ApprovalDecisionRequest(ApprovalDecisionRequest.Decision.APPROVE, null);
        MerchantStatusResponse response = approvalService.reviewDecision(merchantId, request, adminId);

        assertThat(response.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPROVED);

        ArgumentCaptor<MerchantStatusHistory> historyCaptor = ArgumentCaptor.forClass(MerchantStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        MerchantStatusHistory history = historyCaptor.getValue();
        assertThat(history.getFromStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(history.getActorUserId()).isEqualTo(adminId);

        ArgumentCaptor<MerchantStatusChanged> eventCaptor = ArgumentCaptor.forClass(MerchantStatusChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MerchantStatusChanged event = eventCaptor.getValue();
        assertThat(event.merchantId()).isEqualTo(merchantId);
        assertThat(event.fromStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(event.toStatus()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(event.actorUserId()).isEqualTo(adminId);
    }

    @Test
    @DisplayName("Admin decision REJECT with required reason transitions PENDING to REJECTED")
    void rejectPendingMerchant() {
        Merchant merchant = createSampleMerchant(MerchantStatus.PENDING);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalDecisionRequest request = new ApprovalDecisionRequest(
                ApprovalDecisionRequest.Decision.REJECT, "Registration certificate unclear");
        MerchantStatusResponse response = approvalService.reviewDecision(merchantId, request, adminId);

        assertThat(response.status()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(response.decisionReason()).isEqualTo("Registration certificate unclear");
    }

    @Test
    @DisplayName("Admin decision REJECT without reason throws IllegalArgumentException")
    void rejectWithoutReasonFails() {
        Merchant merchant = createSampleMerchant(MerchantStatus.PENDING);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        ApprovalDecisionRequest request = new ApprovalDecisionRequest(ApprovalDecisionRequest.Decision.REJECT, "   ");
        assertThatThrownBy(() -> approvalService.reviewDecision(merchantId, request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");

        verify(merchantRepository, never()).saveAndFlush(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin decision SUSPEND on APPROVED merchant transitions to SUSPENDED with reason")
    void suspendApprovedMerchant() {
        Merchant merchant = createSampleMerchant(MerchantStatus.APPROVED);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalDecisionRequest request = new ApprovalDecisionRequest(
                ApprovalDecisionRequest.Decision.SUSPEND, "Unusual parcel claim activity");
        MerchantStatusResponse response = approvalService.reviewDecision(merchantId, request, adminId);

        assertThat(response.status()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(response.decisionReason()).isEqualTo("Unusual parcel claim activity");
    }

    @Test
    @DisplayName("Admin decision SUSPEND without reason throws IllegalArgumentException")
    void suspendWithoutReasonFails() {
        Merchant merchant = createSampleMerchant(MerchantStatus.APPROVED);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        ApprovalDecisionRequest request = new ApprovalDecisionRequest(ApprovalDecisionRequest.Decision.SUSPEND, null);
        assertThatThrownBy(() -> approvalService.reviewDecision(merchantId, request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    @DisplayName("Illegal transition (e.g. SUSPEND on PENDING) throws IllegalStateTransitionException")
    void illegalTransitionThrowsException() {
        Merchant merchant = createSampleMerchant(MerchantStatus.PENDING);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        ApprovalDecisionRequest request = new ApprovalDecisionRequest(
                ApprovalDecisionRequest.Decision.SUSPEND, "Suspension reason");

        assertThatThrownBy(() -> approvalService.reviewDecision(merchantId, request, adminId))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("Cannot transition merchant status from PENDING to SUSPENDED");

        verify(merchantRepository, never()).saveAndFlush(any());
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Admin decision APPROVE reinstates SUSPENDED merchant back to APPROVED")
    void reinstateSuspendedMerchant() {
        Merchant merchant = createSampleMerchant(MerchantStatus.SUSPENDED);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        ApprovalDecisionRequest request = new ApprovalDecisionRequest(ApprovalDecisionRequest.Decision.APPROVE, null);
        MerchantStatusResponse response = approvalService.reviewDecision(merchantId, request, adminId);

        assertThat(response.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.APPROVED);
    }

    @Test
    @DisplayName("getMerchantDetail returns full merchant profile with complete status audit history")
    void getMerchantDetailWithHistory() {
        Merchant merchant = createSampleMerchant(MerchantStatus.APPROVED);
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantStatusHistory h1 = new MerchantStatusHistory(merchantId, null, MerchantStatus.PENDING, userId, "Application submitted");
        MerchantStatusHistory h2 = new MerchantStatusHistory(merchantId, MerchantStatus.PENDING, MerchantStatus.APPROVED, adminId, null);
        when(historyRepository.findByMerchantIdOrderByCreatedAtAsc(merchantId)).thenReturn(List.of(h1, h2));

        MerchantDetailResponse detail = approvalService.getMerchantDetail(merchantId);

        assertThat(detail.id()).isEqualTo(merchantId);
        assertThat(detail.businessName()).isEqualTo("Acme Store");
        assertThat(detail.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(detail.statusHistory()).hasSize(2);
        assertThat(detail.statusHistory().get(0).toStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(detail.statusHistory().get(1).toStatus()).isEqualTo(MerchantStatus.APPROVED);
    }

    @Test
    @DisplayName("Merchant profile update on APPROVED merchant updates fields without altering status")
    void updateProfileOnApprovedMerchant() {
        Merchant merchant = createSampleMerchant(MerchantStatus.APPROVED);
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        MerchantProfileUpdateRequest updateReq = new MerchantProfileUpdateRequest(
                "Acme Prime Store", "Wholesale", "+94777777777", "999 High St", "Updated desc", "https://new.example.com");

        MerchantStatusResponse updated = approvalService.updateProfile(userId, updateReq);

        assertThat(updated.businessName()).isEqualTo("Acme Prime Store");
        assertThat(updated.businessType()).isEqualTo("Wholesale");
        assertThat(updated.status()).isEqualTo(MerchantStatus.APPROVED);
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Merchant profile update on REJECTED merchant resubmits application to PENDING")
    void updateProfileOnRejectedMerchantResubmits() {
        Merchant merchant = createSampleMerchant(MerchantStatus.REJECTED);
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        MerchantProfileUpdateRequest updateReq = new MerchantProfileUpdateRequest(
                "Acme Corrected Corp", "Retail", "+94771234567", "Corrected Address", null, null);

        MerchantStatusResponse updated = approvalService.updateProfile(userId, updateReq);

        assertThat(updated.status()).isEqualTo(MerchantStatus.PENDING);
        assertThat(updated.businessName()).isEqualTo("Acme Corrected Corp");
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);

        ArgumentCaptor<MerchantStatusHistory> historyCaptor = ArgumentCaptor.forClass(MerchantStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(MerchantStatus.PENDING);

        ArgumentCaptor<MerchantStatusChanged> eventCaptor = ArgumentCaptor.forClass(MerchantStatusChanged.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().toStatus()).isEqualTo(MerchantStatus.PENDING);
    }
}

