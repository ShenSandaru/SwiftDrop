package com.swiftdrop.delivery.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Proofs of delivery, at most one per parcel. */
public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {

    Optional<DeliveryProof> findByParcelId(UUID parcelId);

    boolean existsByParcelId(UUID parcelId);
}
