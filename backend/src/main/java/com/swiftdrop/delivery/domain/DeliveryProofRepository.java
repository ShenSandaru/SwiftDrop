package com.swiftdrop.delivery.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Proofs of delivery, at most one per parcel. */
public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, UUID> {

    Optional<DeliveryProof> findByParcelId(UUID parcelId);

    boolean existsByParcelId(UUID parcelId);

    /** The recipient name only, without loading the photo bytes. */
    @Query("SELECT p.recipientName FROM DeliveryProof p WHERE p.parcelId = :parcelId")
    Optional<String> findRecipientNameByParcelId(@Param("parcelId") UUID parcelId);
}
