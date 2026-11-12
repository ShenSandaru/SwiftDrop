package com.swiftdrop.delivery.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Hubs of the delivery network (ADR 0009 §1). */
public interface HubRepository extends JpaRepository<Hub, UUID> {

    Optional<Hub> findByZoneIdAndActiveTrue(UUID zoneId);

    Optional<Hub> findFirstByCentralTrueAndActiveTrue();

    Optional<Hub> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    List<Hub> findAllByOrderByNameAsc();
}
