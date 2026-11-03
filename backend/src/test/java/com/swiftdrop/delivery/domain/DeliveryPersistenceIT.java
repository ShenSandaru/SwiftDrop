package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Delivery tables against real PostgreSQL: Hibernate accepts the schema, entities round-trip, and
 * the database rejects what the domain must never store. Each test rolls back.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        // Keep the users table empty apart from the test's own users, whatever the local .env says
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
@Transactional
class DeliveryPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private ParcelRepository parcels;
    @Autowired
    private ParcelStatusHistoryRepository history;
    @Autowired
    private DeliveryProofRepository proofs;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    // No pricing implementation on this branch; booking needs one to start
    @MockitoBean
    private FeeCalculator feeCalculator;

    private final TrackingNumberGenerator trackingNumbers = new TrackingNumberGenerator();
    private UUID customerId;
    private UUID courierId;

    @BeforeEach
    void createUsers() {
        customerId = insertUser("customer");
        courierId = insertUser("courier");
    }

    @Test
    void customerParcelRoundTripsWithBothAddressesAndFee() {
        Parcel saved = parcels.saveAndFlush(customerParcel(trackingNumbers.next()));
        entityManager.clear();

        Parcel loaded = parcels.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getTrackingNumber()).isEqualTo(saved.getTrackingNumber());
        assertThat(loaded.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(loaded.getLastSequence()).isEqualTo(1);
        assertThat(loaded.getCustomerId()).isEqualTo(customerId);
        assertThat(loaded.getCreatedBy()).isEqualTo(customerId);
        assertThat(loaded.getPickup().getCity()).isEqualTo("Colombo 05");
        assertThat(loaded.getPickup().getLine2()).isNull();
        assertThat(loaded.getDropoff().getCity()).isEqualTo("Kandy");
        assertThat(loaded.getDropoff().getPostalCode()).isEqualTo("20000");
        assertThat(loaded.getSize()).isEqualTo(ParcelSize.MEDIUM);
        assertThat(loaded.getFeeAmount()).isEqualByComparingTo("650.00");
        assertThat(loaded.getFeeCurrency()).isEqualTo("LKR");
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getVersion()).isZero();
        assertThat(parcels.findByTrackingNumber(saved.getTrackingNumber())).isPresent();
    }

    @Test
    void merchantParcelIsFoundByMerchantAndOrderReference() {
        UUID merchantId = UUID.randomUUID();
        Parcel saved = parcels.saveAndFlush(merchantParcel(trackingNumbers.next(), merchantId, "ORD-1"));

        assertThat(parcels.findByMerchantIdAndMerchantOrderReference(merchantId, "ORD-1"))
                .get().extracting(Parcel::getId).isEqualTo(saved.getId());
        assertThat(parcels.findByMerchantIdAndMerchantOrderReference(UUID.randomUUID(), "ORD-1")).isEmpty();
    }

    @Test
    void historyProofAndOutboxRoundTrip() {
        Parcel parcel = parcels.saveAndFlush(customerParcel(trackingNumbers.next()));
        UUID eventId = UUID.randomUUID();
        history.saveAndFlush(new ParcelStatusHistoryEntry(parcel.getId(), 1, null, ParcelStatus.CREATED, customerId,
                ActorRole.CUSTOMER, null, null, eventId, NOW));
        proofs.saveAndFlush(new DeliveryProof(parcel.getId(), "Kumari Silva", new byte[] {1, 2, 3}, "image/jpeg",
                courierId, NOW));
        outbox.saveAndFlush(new OutboxEvent(eventId, parcel.getId(), 1, "parcel.status-changed",
                "{\"eventId\":\"" + eventId + "\",\"schemaVersion\":1}", NOW));
        entityManager.clear();

        assertThat(history.findByParcelIdOrderBySequenceAsc(parcel.getId()))
                .singleElement().extracting(ParcelStatusHistoryEntry::getEventId).isEqualTo(eventId);
        DeliveryProof proof = proofs.findByParcelId(parcel.getId()).orElseThrow();
        assertThat(proof.getPhoto()).containsExactly(1, 2, 3);
        assertThat(proof.getPhotoSizeBytes()).isEqualTo(3);
        OutboxEvent event = outbox.findByParcelIdOrderBySequenceAsc(parcel.getId()).getFirst();
        assertThat(event.getId()).isNotNull();
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT payload ->> 'schemaVersion' FROM parcel_outbox_events WHERE event_id = ?",
                String.class, eventId)).isEqualTo("1");
    }

    @Test
    void duplicateTrackingNumberIsRejected() {
        String number = trackingNumbers.next();
        parcels.saveAndFlush(customerParcel(number));

        assertThatThrownBy(() -> parcels.saveAndFlush(customerParcel(number)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateMerchantOrderReferenceIsRejectedForTheSameMerchantOnly() {
        UUID merchantId = UUID.randomUUID();
        parcels.saveAndFlush(merchantParcel(trackingNumbers.next(), merchantId, "ORD-1"));
        parcels.saveAndFlush(merchantParcel(trackingNumbers.next(), UUID.randomUUID(), "ORD-1"));

        assertThatThrownBy(() -> parcels.saveAndFlush(merchantParcel(trackingNumbers.next(), merchantId, "ORD-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void customerMustBeAnExistingUser() {
        Parcel orphan = Parcel.forCustomer(trackingNumbers.next(), UUID.randomUUID(), address("Colombo 05", null),
                address("Kandy", "20000"), ParcelSize.SMALL, 1000, null, fee(), NOW);

        assertThatThrownBy(() -> parcels.saveAndFlush(orphan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidParcelStates() {
        Parcel parcel = parcels.saveAndFlush(customerParcel(trackingNumbers.next()));

        // Past CREATED without a courier (ADR 0006 §1)
        assertViolation("UPDATE parcels SET status = 'PICKED_UP' WHERE id = ?", parcel.getId());
        // A customer parcel with a merchant owner as well
        assertViolation("UPDATE parcels SET merchant_id = gen_random_uuid() WHERE id = ?", parcel.getId());
        // Malformed tracking number
        assertViolation("UPDATE parcels SET tracking_number = 'SD0000000000I' WHERE id = ?", parcel.getId());
        assertViolation("UPDATE parcels SET weight_grams = 30001 WHERE id = ?", parcel.getId());
    }

    @Test
    void historyRejectsDuplicateSequenceAndFailedWithoutReason() {
        Parcel parcel = parcels.saveAndFlush(customerParcel(trackingNumbers.next()));
        history.saveAndFlush(new ParcelStatusHistoryEntry(parcel.getId(), 1, null, ParcelStatus.CREATED, customerId,
                ActorRole.CUSTOMER, null, null, UUID.randomUUID(), NOW));

        assertViolation("INSERT INTO parcel_status_history (id, parcel_id, sequence, from_status, to_status, actor_role, "
                + "event_id, occurred_at) VALUES (gen_random_uuid(), ?, 1, NULL, 'CREATED', 'CUSTOMER', "
                + "gen_random_uuid(), now())", parcel.getId());
        assertViolation("INSERT INTO parcel_status_history (id, parcel_id, sequence, from_status, to_status, actor_role, "
                + "event_id, occurred_at) VALUES (gen_random_uuid(), ?, 2, 'CREATED', 'FAILED', 'COURIER', "
                + "gen_random_uuid(), now())", parcel.getId());
        assertThat(history.findByParcelIdOrderBySequenceAsc(parcel.getId())).hasSize(1);
        assertThat(FailureReason.values()).hasSize(5);
    }

    @Test
    void secondProofForTheSameParcelIsRejected() {
        Parcel parcel = parcels.saveAndFlush(customerParcel(trackingNumbers.next()));
        proofs.saveAndFlush(new DeliveryProof(parcel.getId(), "Kumari", null, null, courierId, NOW));

        assertThatThrownBy(() -> proofs.saveAndFlush(new DeliveryProof(parcel.getId(), "Kumari", null, null,
                courierId, NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertViolation(String sql, Object... args) {
        // A savepoint keeps the test transaction usable after the expected error
        jdbc.execute("SAVEPOINT expected_violation");
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT expected_violation");
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, ?)",
                id, name + "-" + id + "@example.com", "Test " + name, Timestamp.from(NOW));
        return id;
    }

    private Parcel customerParcel(String trackingNumber) {
        return Parcel.forCustomer(trackingNumber, customerId, address("Colombo 05", null), address("Kandy", "20000"),
                ParcelSize.MEDIUM, 2000, "Books", fee(), NOW);
    }

    private static Parcel merchantParcel(String trackingNumber, UUID merchantId, String reference) {
        return Parcel.forMerchant(trackingNumber, merchantId, reference, address("Colombo 05", null),
                address("Kandy", "20000"), ParcelSize.SMALL, 1000, null, fee(), NOW);
    }

    private static ParcelAddress address(String city, String postalCode) {
        return new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, UUID.randomUUID(),
                postalCode);
    }

    private static Parcel.Fee fee() {
        return new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID());
    }
}
