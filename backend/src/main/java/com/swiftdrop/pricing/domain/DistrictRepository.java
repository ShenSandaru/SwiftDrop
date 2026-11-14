package com.swiftdrop.pricing.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Persistence for districts and their postal delivery zones.
 */
public interface DistrictRepository extends JpaRepository<District, String> {

    @Query("SELECT DISTINCT d FROM District d LEFT JOIN FETCH d.postalZones ORDER BY d.name ASC")
    List<District> findAllWithPostalZones();

    Optional<District> findByCodeIgnoreCase(String code);

    Optional<District> findByNameIgnoreCase(String name);
}

