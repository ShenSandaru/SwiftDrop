package com.swiftdrop.merchant.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Manual redelivery audit records (ADR 0004 §7.5). */
public interface MerchantWebhookRedeliveryRepository extends JpaRepository<MerchantWebhookRedelivery, UUID> {

    List<MerchantWebhookRedelivery> findByDeliveryIdOrderBySeriesAsc(UUID deliveryId);
}
