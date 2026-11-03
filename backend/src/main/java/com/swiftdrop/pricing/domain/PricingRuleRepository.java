package com.swiftdrop.pricing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for pricing rules.
 */
@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    Optional<PricingRule> findByOriginZoneIdAndDestinationZoneId(UUID originZoneId, UUID destinationZoneId);

    Optional<PricingRule> findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(UUID originZoneId, UUID destinationZoneId);

    boolean existsByOriginZoneIdAndDestinationZoneId(UUID originZoneId, UUID destinationZoneId);

    List<PricingRule> findByOriginZoneId(UUID originZoneId);

    List<PricingRule> findByActiveTrue();
}

