package com.swiftdrop.pricing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for global pricing configuration.
 */
public interface PricingConfigRepository extends JpaRepository<PricingConfig, String> {
}

