package com.swiftdrop.merchant.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.swiftdrop.merchant.api.MerchantStatusChanged;

/**
 * Event listener demonstrating transactional safety for merchant status lifecycle events (Phase 3).
 * Uses {@link TransactionPhase#AFTER_COMMIT} so consumers never react to rolled-back transactions.
 */
@Component
public class MerchantStatusEventListener {

    private static final Logger log = LoggerFactory.getLogger(MerchantStatusEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMerchantStatusChanged(MerchantStatusChanged event) {
        log.info("Merchant status change confirmed post-commit: merchantId={}, from={}, to={}, actor={}",
                event.merchantId(), event.fromStatus(), event.toStatus(), event.actorUserId());
    }
}

