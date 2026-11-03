package com.swiftdrop.pricing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for delivery zones.
 */
@Repository
public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    Optional<Zone> findByCode(String code);

    Optional<Zone> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    List<Zone> findByActiveTrue();
}

