package com.swiftdrop.merchant.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.api.MerchantStatusChanged;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistory;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.web.ApprovalDecisionRequest;
import com.swiftdrop.merchant.web.MerchantDetailResponse;
import com.swiftdrop.merchant.web.MerchantProfileUpdateRequest;
import com.swiftdrop.merchant.web.MerchantStatusResponse;

/**
 * Service managing the merchant approval lifecycle, state machine transitions,
 * audit history recording, and merchant profile self-service (Phase 3).
 */
@Service
public class MerchantApprovalService {

    private static final Logger log = LoggerFactory.getLogger(MerchantApprovalService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MerchantApprovalService(
            MerchantRepository merchantRepository,
            MerchantStatusHistoryRepository historyRepository,
            ApplicationEventPublisher eventPublisher) {
        this.merchantRepository = merchantRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Lists merchants optionally filtered by status with pagination (Admin only).
     */
    @Transactional(readOnly = true)
    public Page<MerchantStatusResponse> findMerchants(MerchantStatus status, Pageable pageable) {
        Page<Merchant> page = status != null
                ? merchantRepository.findByStatusOrderByCreatedAtAsc(status, pageable)
                : merchantRepository.findAll(pageable);
        return page.map(MerchantApprovalService::toStatusResponse);
    }

    /**
     * Retrieves full merchant details including complete status audit history (Admin only).
     */
    @Transactional(readOnly = true)
    public MerchantDetailResponse getMerchantDetail(UUID merchantId) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found with ID: " + merchantId));

        List<MerchantStatusHistory> historyList = historyRepository.findByMerchantIdOrderByCreatedAtAsc(merchantId);
        List<MerchantDetailResponse.StatusHistoryItem> historyItems = historyList.stream()
                .map(h -> new MerchantDetailResponse.StatusHistoryItem(
                        h.getId(),
                        h.getMerchantId(),
                        h.getFromStatus(),
                        h.getToStatus(),
                        h.getActorUserId(),
                        h.getReason(),
                        h.getCreatedAt()))
                .toList();

        return new MerchantDetailResponse(
                merchant.getId(),
                merchant.getUserId(),
                merchant.getBusinessName(),
                merchant.getBusinessType(),
                merchant.getBusinessRegistrationNo(),
                merchant.getContactPhone(),
                merchant.getBusinessAddress(),
                merchant.getBusinessDescription(),
                merchant.getWebsiteUrl(),
                merchant.getStatus(),
                merchant.getStatusReason(),
                merchant.getDecidedBy(),
                merchant.getDecidedAt(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt(),
                merchant.getVersion(),
                historyItems);
    }

    /**
     * Retrieves the current merchant status for the authenticated user.
     */
    @Transactional(readOnly = true)
    public MerchantStatusResponse getMerchantStatusForUser(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return merchantRepository.findByUserId(userId)
                .map(MerchantApprovalService::toStatusResponse)
                .orElse(null);
    }

    /**
     * Executes an administrative approval decision (APPROVE, REJECT, or SUSPEND).
     * Validates state transitions via domain rules and enforces optimistic locking.
     */
    @Transactional
    public MerchantStatusResponse reviewDecision(UUID merchantId, ApprovalDecisionRequest request, UUID adminUserId) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(adminUserId, "adminUserId must not be null");

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found with ID: " + merchantId));

        MerchantStatus fromStatus = merchant.getStatus();
        MerchantStatus targetStatus = resolveTargetStatus(request.decision());

        // Validate reason requirement for rejection and suspension
        if ((request.decision() == ApprovalDecisionRequest.Decision.REJECT
                || request.decision() == ApprovalDecisionRequest.Decision.SUSPEND)
                && (request.reason() == null || request.reason().isBlank())) {
            throw new IllegalArgumentException("A reason is required when rejecting or suspending a merchant");
        }

        // Domain state machine transition (throws IllegalStateTransitionException on invalid transitions)
        merchant.transitionTo(targetStatus, adminUserId, request.reason());

        // Flush to enforce optimistic locking checks against concurrent admin reviews
        merchant = merchantRepository.saveAndFlush(merchant);

        // Record immutable audit history
        MerchantStatusHistory history = new MerchantStatusHistory(
                merchant.getId(),
                fromStatus,
                merchant.getStatus(),
                adminUserId,
                request.reason());
        historyRepository.save(history);

        // Publish lifecycle event for interested consumers (e.g. Member B credential revocation)
        eventPublisher.publishEvent(new MerchantStatusChanged(
                merchant.getId(),
                merchant.getUserId(),
                fromStatus,
                merchant.getStatus(),
                adminUserId,
                request.reason(),
                Instant.now()));

        log.info("Merchant review decision committed: merchantId={}, fromStatus={}, toStatus={}, adminUserId={}",
                merchantId, fromStatus, merchant.getStatus(), adminUserId);

        return toStatusResponse(merchant);
    }

    /**
     * Merchant self-service profile update.
     * If current status is REJECTED, this automatically resubmits the application and transitions it back to PENDING.
     */
    @Transactional
    public MerchantStatusResponse updateProfile(UUID userId, MerchantProfileUpdateRequest request) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant profile not found for user: " + userId));

        MerchantStatus previousStatus = merchant.getStatus();

        merchant.updateProfile(
                request.businessName(),
                request.businessType(),
                request.contactPhone(),
                request.businessAddress(),
                request.businessDescription(),
                request.websiteUrl());

        // Phase 3 Rule: If REJECTED, profile update resubmits application to PENDING
        if (previousStatus == MerchantStatus.REJECTED) {
            String resubmitReason = "Application resubmitted with updated profile";
            merchant.transitionTo(MerchantStatus.PENDING, userId, resubmitReason);

            merchant = merchantRepository.saveAndFlush(merchant);

            MerchantStatusHistory history = new MerchantStatusHistory(
                    merchant.getId(),
                    previousStatus,
                    MerchantStatus.PENDING,
                    userId,
                    resubmitReason);
            historyRepository.save(history);

            eventPublisher.publishEvent(new MerchantStatusChanged(
                    merchant.getId(),
                    merchant.getUserId(),
                    previousStatus,
                    MerchantStatus.PENDING,
                    userId,
                    resubmitReason,
                    Instant.now()));

            log.info("Rejected merchant resubmitted application: merchantId={}, newStatus=PENDING", merchant.getId());
        } else {
            merchant = merchantRepository.saveAndFlush(merchant);
        }

        return toStatusResponse(merchant);
    }

    private static MerchantStatus resolveTargetStatus(ApprovalDecisionRequest.Decision decision) {
        return switch (decision) {
            case APPROVE -> MerchantStatus.APPROVED;
            case REJECT -> MerchantStatus.REJECTED;
            case SUSPEND -> MerchantStatus.SUSPENDED;
        };
    }

    public static MerchantStatusResponse toStatusResponse(Merchant merchant) {
        String decisionReason = (merchant.getStatus() == MerchantStatus.REJECTED || merchant.getStatus() == MerchantStatus.SUSPENDED)
                ? merchant.getStatusReason()
                : null;

        return new MerchantStatusResponse(
                merchant.getId(),
                merchant.getStatus(),
                merchant.getBusinessName(),
                merchant.getBusinessType(),
                merchant.getBusinessRegistrationNo(),
                merchant.getContactPhone(),
                merchant.getBusinessAddress(),
                merchant.getBusinessDescription(),
                merchant.getWebsiteUrl(),
                merchant.getCreatedAt(),
                merchant.getDecidedAt(),
                decisionReason);
    }
}

