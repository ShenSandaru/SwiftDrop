package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistory;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpoint;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

import jakarta.persistence.EntityManager;

import com.swiftdrop.pricing.api.FeeCalculator;

/**
 * Verifies that:
 * 1. An isolated PostgreSQL 18 container starts.
 * 2. Flyway applies all migrations from {@code classpath:db/migration}.
 * 3. All identity and pricing migrations are recorded in {@code flyway_schema_history}.
 * 4. Hibernate validates all JPA entities against the schema with {@code ddl-auto=validate}.
 * 5. Domain entities, foreign keys, unique constraints, and check constraints function correctly.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class DatabaseMigrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private PricingRuleRepository pricingRuleRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantStatusHistoryRepository merchantStatusHistoryRepository;

    @Autowired
    private MerchantWebhookEndpointRepository merchantWebhookEndpointRepository;

    // Delivery's booking needs pricing, which may not be on the classpath yet
    @MockitoBean
    private FeeCalculator feeCalculator;

    @Test
    @DisplayName("Flyway applies all identity and pricing migrations in order, and Hibernate validates successfully")
    void flywayAppliesAllMigrationsAndHibernateValidationSucceeds() {
        // Assert PostgreSQL container is running
        assertThat(postgres.isRunning()).isTrue();

        // Assert Flyway schema history recorded identity, pricing and delivery migrations in order
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, description, type, script, success FROM flyway_schema_history ORDER BY installed_rank ASC");

        assertThat(rows).hasSize(19);

        assertThat(rows.get(0))
                .containsEntry("version", "202610251600")
                .containsEntry("description", "identity create users and roles")
                .containsEntry("type", "SQL")
                .containsEntry("script", "identity/V202610251600__identity_create_users_and_roles.sql")
                .containsEntry("success", true);

        assertThat(rows.get(1))
                .containsEntry("version", "202610251700")
                .containsEntry("description", "identity create external identities")
                .containsEntry("type", "SQL")
                .containsEntry("script", "identity/V202610251700__identity_create_external_identities.sql")
                .containsEntry("success", true);

        assertThat(rows.get(2))
                .containsEntry("version", "202610261800")
                .containsEntry("description", "identity create google login codes")
                .containsEntry("type", "SQL")
                .containsEntry("script", "identity/V202610261800__identity_create_google_login_codes.sql")
                .containsEntry("success", true);

        assertThat(rows.get(3))
                .containsEntry("version", "202611021200")
                .containsEntry("description", "pricing create zones and pricing rules")
                .containsEntry("type", "SQL")
                .containsEntry("script", "pricing/V202611021200__pricing_create_zones_and_pricing_rules.sql")
                .containsEntry("success", true);

        assertThat(rows.subList(4, 8))
                .extracting(row -> row.get("script"), row -> row.get("success"))
                .containsExactly(
                        tuple("delivery/V202611031000__delivery_create_parcels.sql", true),
                        tuple("delivery/V202611031010__delivery_create_parcel_status_history.sql", true),
                        tuple("delivery/V202611031020__delivery_create_delivery_proofs.sql", true),
                        tuple("delivery/V202611031030__delivery_create_parcel_outbox_events.sql", true));

        assertThat(rows.subList(8, 12))
                .extracting(row -> row.get("script"), row -> row.get("success"))
                .containsExactly(
                        tuple("merchant/V202611041000__merchant_create_merchants.sql", true),
                        tuple("merchant/V202611041010__merchant_create_merchant_status_history.sql", true),
                        tuple("merchant/V202611041020__merchant_create_merchant_webhook_endpoints.sql", true),
                        tuple("merchant/V202611041030__merchant_create_merchant_webhook_endpoint_events.sql", true));

        assertThat(rows.subList(12, 14))
                .extracting(row -> row.get("script"), row -> row.get("success"))
                .containsExactly(
                        tuple("delivery/V202611041200__delivery_add_dispatch_assignment_history.sql", true),
                        tuple("delivery/V202611051210__delivery_add_parcel_assignment_version.sql", true));

        assertThat(rows.subList(14, 16))
                .extracting(row -> row.get("script"), row -> row.get("success"))
                .containsExactly(
                        tuple("identity/V202611101200__identity_replace_dispatcher_admin_with_admin_and_dispatcher.sql", true),
                        tuple("delivery/V202611101210__delivery_update_actor_role_for_admin_and_dispatcher.sql", true));

        assertThat(rows.get(16))
                .containsEntry("script", "delivery/V202611111000__delivery_create_hubs.sql")
                .containsEntry("success", true);
        assertThat(rows.get(17))
                .containsEntry("script", "delivery/V202611111010__delivery_add_hub_lifecycle.sql")
                .containsEntry("success", true);
        assertThat(rows.get(18))
                .containsEntry("script", "pricing/V202611131400__pricing_create_districts_and_postal_zones.sql")
                .containsEntry("success", true);

        // Assert tables exist in the isolated test database
        Integer usersCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(usersCount).isNotNull();

        Integer rolesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        assertThat(rolesCount).isNotNull().isEqualTo(5);

        List<String> roleNames = jdbcTemplate.queryForList("SELECT name FROM roles ORDER BY name", String.class);
        assertThat(roleNames).containsExactlyInAnyOrder("CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN");

        Integer singleAdminIndexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_user_roles_single_admin'", Integer.class);
        assertThat(singleAdminIndexCount).isEqualTo(1);

        Integer zonesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM zones", Integer.class);
        assertThat(zonesCount).isNotNull();

        Integer districtsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM districts", Integer.class);
        assertThat(districtsCount).isEqualTo(25);

        Integer pricingRulesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pricing_rules", Integer.class);
        assertThat(pricingRulesCount).isNotNull();

        Integer merchantsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM merchants", Integer.class);
        assertThat(merchantsCount).isNotNull();

        Integer historyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM merchant_status_history", Integer.class);
        assertThat(historyCount).isNotNull();

        Integer endpointsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM merchant_webhook_endpoints", Integer.class);
        assertThat(endpointsCount).isNotNull();

        Integer endpointEventsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM merchant_webhook_endpoint_events", Integer.class);
        assertThat(endpointEventsCount).isNotNull();

        // Assert Hibernate EntityManager is active and connected
        assertThat(entityManager.isOpen()).isTrue();
    }

    @Test
    @DisplayName("Zone entity persists correctly and enforces unique code and unique name constraints")
    void zoneEntityCrudAndUniqueConstraints() {
        Zone colombo = zoneRepository.save(new Zone("CMB", "Colombo"));
        assertThat(colombo.getId()).isNotNull();
        assertThat(colombo.getCreatedAt()).isNotNull();
        assertThat(colombo.getVersion()).isZero();

        // Enforce unique code constraint
        assertThatThrownBy(() -> {
            zoneRepository.saveAndFlush(new Zone("CMB", "Colombo Greater"));
        }).isInstanceOf(DataIntegrityViolationException.class);

        // Enforce unique name constraint
        assertThatThrownBy(() -> {
            zoneRepository.saveAndFlush(new Zone("CMB_ALT", "Colombo"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PricingRule entity persists and can be queried by origin and destination zones")
    void pricingRuleEntityPersistenceAndLookup() {
        Zone origin = zoneRepository.save(new Zone("WEST", "Western Province"));
        Zone destination = zoneRepository.save(new Zone("CENTRAL", "Central Province"));

        PricingRule rule = new PricingRule(
                origin.getId(),
                destination.getId(),
                new BigDecimal("450.00"),
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                new BigDecimal("50.00"),
                new BigDecimal("100.00"),
                "LKR"
        );
        PricingRule savedRule = pricingRuleRepository.save(rule);

        assertThat(savedRule.getId()).isNotNull();
        assertThat(savedRule.getCreatedAt()).isNotNull();
        assertThat(savedRule.getVersion()).isZero();

        Optional<PricingRule> retrieved = pricingRuleRepository
                .findByOriginZoneIdAndDestinationZoneId(origin.getId(), destination.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getBaseFee()).isEqualByComparingTo("450.00");
        assertThat(retrieved.get().getPerKgFee()).isEqualByComparingTo("100.00");
        assertThat(retrieved.get().getSmallSizeSurcharge()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Enforce unique constraint on (origin_zone_id, destination_zone_id)")
    void uniqueZonePairConstraintEnforced() {
        Zone origin = zoneRepository.save(new Zone("Z1", "Zone 1"));
        Zone destination = zoneRepository.save(new Zone("Z2", "Zone 2"));

        pricingRuleRepository.saveAndFlush(new PricingRule(
                origin.getId(),
                destination.getId(),
                new BigDecimal("300.00"),
                new BigDecimal("50.00")
        ));

        // Attempt duplicate zone pair
        assertThatThrownBy(() -> {
            pricingRuleRepository.saveAndFlush(new PricingRule(
                    origin.getId(),
                    destination.getId(),
                    new BigDecimal("400.00"),
                    new BigDecimal("60.00")
            ));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Enforce foreign key constraints referencing valid zones")
    void foreignKeyConstraintsEnforced() {
        UUID nonExistentZoneId = UUID.randomUUID();
        Zone validZone = zoneRepository.save(new Zone("VALID", "Valid Zone"));

        assertThatThrownBy(() -> {
            pricingRuleRepository.saveAndFlush(new PricingRule(
                    nonExistentZoneId,
                    validZone.getId(),
                    new BigDecimal("300.00"),
                    new BigDecimal("50.00")
            ));
        }).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> {
            pricingRuleRepository.saveAndFlush(new PricingRule(
                    validZone.getId(),
                    nonExistentZoneId,
                    new BigDecimal("300.00"),
                    new BigDecimal("50.00")
            ));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Enforce check constraint on non-negative fee amounts at the database level")
    void checkConstraintPositiveAmountsEnforced() {
        Zone origin = zoneRepository.save(new Zone("SRC", "Source"));
        Zone destination = zoneRepository.save(new Zone("DST", "Destination"));

        // Direct SQL insert attempting negative base_fee
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO pricing_rules (id, origin_zone_id, destination_zone_id, base_fee, per_kg_fee, small_size_surcharge, medium_size_surcharge, large_size_surcharge, currency, active, version, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    UUID.randomUUID(), origin.getId(), destination.getId(), -10.00, 50.00, 0.00, 0.00, 0.00, "LKR", true, 0
            );
        }).isInstanceOf(DataIntegrityViolationException.class);

        // Direct SQL insert attempting negative per_kg_fee
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO pricing_rules (id, origin_zone_id, destination_zone_id, base_fee, per_kg_fee, small_size_surcharge, medium_size_surcharge, large_size_surcharge, currency, active, version, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    UUID.randomUUID(), origin.getId(), destination.getId(), 100.00, -5.00, 0.00, 0.00, 0.00, "LKR", true, 0
            );
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Merchant entity persists correctly and enforces unique user_id and registration number")
    void merchantEntityPersistenceAndUniqueConstraints() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                userId, "merchant" + userId + "@example.com", "hash", "Merchant User", true);

        Merchant merchant = new Merchant(
                userId, "Test Store", "Retail", "REG-" + userId, "+94771234567", "123 Business Way", "Test Description", "https://store.example.com");
        Merchant saved = merchantRepository.saveAndFlush(merchant);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(saved.getBusinessType()).isEqualTo("Retail");
        assertThat(saved.getVersion()).isZero();

        // Enforce unique user_id
        assertThatThrownBy(() -> {
            merchantRepository.saveAndFlush(new Merchant(
                    userId, "Another Store", "Retail", "REG-OTHER-" + userId, "+94771234568", "456 Other Way", null, null));
        }).isInstanceOf(DataIntegrityViolationException.class);

        // Enforce unique registration number
        UUID anotherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                anotherUserId, "merchant" + anotherUserId + "@example.com", "hash", "Merchant User 2", true);

        assertThatThrownBy(() -> {
            merchantRepository.saveAndFlush(new Merchant(
                    anotherUserId, "Duplicate Reg Store", "Retail", "REG-" + userId, "+94771234569", "789 Third Way", null, null));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Enforce check constraint on merchant status at database level")
    void merchantStatusCheckConstraintEnforced() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                userId, "merchant" + userId + "@example.com", "hash", "Merchant User", true);

        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO merchants (id, user_id, business_name, business_type, business_registration_no, contact_phone, business_address, status, created_at, updated_at, version) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)",
                    UUID.randomUUID(), userId, "Bad Status Store", "Retail", "REG-BAD-" + userId, "+94771234567", "Address", "INVALID_STATUS");
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("MerchantWebhookEndpoint persists with subscribed event types collection and supports soft delete")
    void merchantWebhookEndpointPersistenceAndEvents() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                userId, "merchant" + userId + "@example.com", "hash", "Merchant User", true);

        Merchant merchant = merchantRepository.saveAndFlush(new Merchant(
                userId, "Hook Store", "Retail", "REG-HOOK-" + userId, "+94771234567", "Hook Way", null, null));

        MerchantWebhookEndpoint endpoint = new MerchantWebhookEndpoint(
                merchant.getId(),
                "https://hook.example.com/events",
                "Order status callback",
                "cipher-bytes",
                "iv-bytes",
                1,
                "3a9f",
                Set.of(ParcelStatus.CREATED, ParcelStatus.DELIVERED));

        MerchantWebhookEndpoint saved = merchantWebhookEndpointRepository.saveAndFlush(endpoint);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEvents()).containsExactlyInAnyOrder(ParcelStatus.CREATED, ParcelStatus.DELIVERED);

        // Verify active query for CREATED event
        List<MerchantWebhookEndpoint> active = merchantWebhookEndpointRepository
                .findActiveForMerchantAndEvent(merchant.getId(), ParcelStatus.CREATED);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo(saved.getId());

        // Verify active query for non-subscribed event
        List<MerchantWebhookEndpoint> none = merchantWebhookEndpointRepository
                .findActiveForMerchantAndEvent(merchant.getId(), ParcelStatus.FAILED);
        assertThat(none).isEmpty();

        // Soft delete endpoint
        saved.softDelete();
        merchantWebhookEndpointRepository.saveAndFlush(saved);

        // Active query ignores soft-deleted endpoints
        List<MerchantWebhookEndpoint> afterDelete = merchantWebhookEndpointRepository
                .findActiveForMerchantAndEvent(merchant.getId(), ParcelStatus.CREATED);
        assertThat(afterDelete).isEmpty();
    }
}
