package com.swiftdrop.merchant.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.swiftdrop.merchant.api.MerchantStatus;

/**
 * Spring Data JPA repository for merchant business profiles (ADR 0003).
 */
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByBusinessRegistrationNo(String businessRegistrationNo);

    Page<Merchant> findByStatusOrderByCreatedAtAsc(MerchantStatus status, Pageable pageable);
}

