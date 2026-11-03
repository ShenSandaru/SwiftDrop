package com.swiftdrop.delivery.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Outbox rows; written with status changes, published by the relay (ADR 0004 §3). */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByParcelIdOrderBySequenceAsc(UUID parcelId);
}
