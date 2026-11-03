package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        "swiftdrop.security.google.client-id="
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

        assertThat(rows).hasSize(8);

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

        // Assert tables exist in the isolated test database
        Integer usersCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(usersCount).isNotNull().isEqualTo(0);

        Integer rolesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        assertThat(rolesCount).isNotNull().isEqualTo(4);

        Integer zonesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM zones", Integer.class);
        assertThat(zonesCount).isNotNull();

        Integer pricingRulesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pricing_rules", Integer.class);
        assertThat(pricingRulesCount).isNotNull();

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
}
