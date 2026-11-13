package com.swiftdrop.merchant.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Quarantined events (SD-410). */
public interface MerchantEventAnomalyRepository extends JpaRepository<MerchantEventAnomaly, UUID> {

    List<MerchantEventAnomaly> findByEventId(UUID eventId);
}
