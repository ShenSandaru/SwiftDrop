package com.swiftdrop.merchant.application;

import java.time.Instant;
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
import com.swiftdrop.merchant.web.MerchantApprovalRequest;
import com.swiftdrop.merchant.web.MerchantStatusResponse;

/**
 * Service for administrative review and merchant status lookups (MER-1, docs/merchant-contracts.md).
 */
@Service
public class MerchantAdminService {

    private static final Logger log = LoggerFactory.getLogger(MerchantAdminService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MerchantAdminService(
            MerchantRepository merchantRepository,
            MerchantStatusHistoryRepository historyRepository,
            ApplicationEventPublisher eventPublisher) {
        this.merchantRepository = merchantRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    public Page<MerchantStatusResponse> findMerchants(MerchantStatus status, Pageable pageable) {
        Page<Merchant> page = status != null
                ? merchantRepository.findByStatusOrderByCreatedAtAsc(status, pageable)
                : merchantRepository.findAll(pageable);
        return page.map(MerchantAdminService::toStatusResponse);
    }

    public MerchantStatusResponse getMerchantStatusForUser(UUID userId) {
        return merchantRepository.findByUserId(userId)
                .map(MerchantAdminService::toStatusResponse)
                .orElse(null);
    }

    public MerchantStatusResponse getMerchantStatus(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .map(MerchantAdminService::toStatusResponse)
                .orElse(null);
    }

    @Transactional
    public MerchantStatusResponse reviewApplication(UUID merchantId, MerchantApprovalRequest request, UUID adminUserId) {
        Objects.requireNonNull(merchantId, "merchantId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(adminUserId, "adminUserId must not be null");

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found with ID: " + merchantId));

        MerchantStatus fromStatus = merchant.getStatus();

        if (request.decision() == MerchantApprovalRequest.Decision.APPROVE) {
            merchant.approve(adminUserId);
        } else if (request.decision() == MerchantApprovalRequest.Decision.REJECT) {
            String reason = request.reason();
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Rejection reason is required");
            }
            merchant.reject(adminUserId, reason);
        } else {
            throw new IllegalArgumentException("Unsupported decision: " + request.decision());
        }

        merchant = merchantRepository.saveAndFlush(merchant);

        MerchantStatusHistory history = new MerchantStatusHistory(
                merchant.getId(),
                fromStatus,
                merchant.getStatus(),
                adminUserId,
                request.reason());
        historyRepository.save(history);

        eventPublisher.publishEvent(new MerchantStatusChanged(
                merchant.getId(),
                merchant.getUserId(),
                fromStatus,
                merchant.getStatus(),
                adminUserId,
                request.reason(),
                Instant.now()));

        log.info("Merchant application reviewed: merchantId={}, newStatus={}", merchantId, merchant.getStatus());

        return toStatusResponse(merchant);
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

