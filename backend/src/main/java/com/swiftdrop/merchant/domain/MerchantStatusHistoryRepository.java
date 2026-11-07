package com.swiftdrop.merchant.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for merchant approval status audit history.
 */
public interface MerchantStatusHistoryRepository extends JpaRepository<MerchantStatusHistory, UUID> {

    List<MerchantStatusHistory> findByMerchantIdOrderByCreatedAtAsc(UUID merchantId);
}

