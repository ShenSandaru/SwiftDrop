package com.swiftdrop.merchant.application;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.merchant.api.MerchantApplicationSubmitted;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistory;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.web.MerchantApplicationRequest;
import com.swiftdrop.merchant.web.MerchantApplicationResponse;

/**
 * Handles merchant registration application submission (MER-1, docs/merchant-contracts.md).
 * Creates the user with the MERCHANT role via UserDirectory and persists the merchant profile in PENDING status.
 */
@Service
public class MerchantRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MerchantRegistrationService.class);

    private final UserDirectory userDirectory;
    private final MerchantRepository merchantRepository;
    private final MerchantStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MerchantRegistrationService(
            UserDirectory userDirectory,
            MerchantRepository merchantRepository,
            MerchantStatusHistoryRepository historyRepository,
            ApplicationEventPublisher eventPublisher) {
        this.userDirectory = userDirectory;
        this.merchantRepository = merchantRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MerchantApplicationResponse register(MerchantApplicationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String normalizedRegNo = request.businessRegistrationNo().trim();

        // Pre-check for duplicate registration number
        if (merchantRepository.existsByBusinessRegistrationNo(normalizedRegNo)) {
            log.warn("Merchant registration failed: duplicate business registration number");
            throw new DuplicateRegistrationException("We couldn't complete this registration.");
        }

        UUID userId;
        try {
            userId = userDirectory.createMerchantUser(
                    request.fullName().trim(),
                    normalizedEmail,
                    request.password());
        } catch (RuntimeException e) {
            log.warn("Merchant registration failed during user creation: {}", e.getClass().getSimpleName());
            throw new DuplicateRegistrationException("We couldn't complete this registration.", e);
        }

        Merchant merchant = new Merchant(
                userId,
                request.businessName(),
                request.businessType(),
                normalizedRegNo,
                request.contactPhone(),
                request.businessAddress(),
                request.businessDescription(),
                request.websiteUrl());

        try {
            merchant = merchantRepository.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException e) {
            log.warn("Merchant registration failed due to database constraint violation");
            throw new DuplicateRegistrationException("We couldn't complete this registration.", e);
        }

        MerchantStatusHistory history = new MerchantStatusHistory(
                merchant.getId(),
                null,
                merchant.getStatus(),
                userId,
                "Application submitted");
        historyRepository.save(history);

        eventPublisher.publishEvent(new MerchantApplicationSubmitted(
                UUID.randomUUID(),
                merchant.getId(),
                userId,
                merchant.getBusinessName(),
                normalizedEmail,
                merchant.getCreatedAt()));

        log.info("Merchant application submitted: merchantId={}, status={}", merchant.getId(), merchant.getStatus());

        return new MerchantApplicationResponse(merchant.getId(), merchant.getStatus());
    }
}

