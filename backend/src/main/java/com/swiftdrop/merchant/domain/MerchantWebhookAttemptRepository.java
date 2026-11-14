package com.swiftdrop.merchant.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Webhook attempts (ADR 0004 §7.1). */
public interface MerchantWebhookAttemptRepository extends JpaRepository<MerchantWebhookAttempt, UUID> {

    List<MerchantWebhookAttempt> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);
}
