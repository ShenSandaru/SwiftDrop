package com.swiftdrop.merchant.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.swiftdrop.merchant.api.MerchantLookup;
import com.swiftdrop.merchant.api.MerchantView;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;

/**
 * Default implementation of {@link MerchantLookup} published facade (ADR 0003, docs/merchant-contracts.md).
 */
@Service
public class MerchantLookupImpl implements MerchantLookup {

    private final MerchantRepository merchantRepository;

    public MerchantLookupImpl(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    @Override
    public Optional<MerchantView> findById(UUID merchantId) {
        return merchantRepository.findById(merchantId).map(this::toView);
    }

    @Override
    public Optional<MerchantView> findByUserId(UUID userId) {
        return merchantRepository.findByUserId(userId).map(this::toView);
    }

    @Override
    public boolean isApproved(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .map(Merchant::isApproved)
                .orElse(false);
    }

    private MerchantView toView(Merchant merchant) {
        return new MerchantView(
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
                merchant.getCreatedAt());
    }
}

