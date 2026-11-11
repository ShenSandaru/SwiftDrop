package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.pricing.api.FeeCalculator;

/**
 * Validates Phase 1 Flyway migration semantics, role replacement invariants,
 * legacy account cleanup, admin identity preservation, and database constraints.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class RoleMigrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private FeeCalculator feeCalculator;

    @Test
    @DisplayName("Fresh database migration creates 5 roles and removes DISPATCHER_ADMIN")
    void freshDatabaseHasFiveRolesAndNoDispatcherAdmin() {
        List<String> roles = jdbcTemplate.queryForList("SELECT name FROM roles ORDER BY name", String.class);
        assertThat(roles).containsExactlyInAnyOrder("CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN");
        assertThat(roles).doesNotContain("DISPATCHER_ADMIN");

        Integer singleAdminIndexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_user_roles_single_admin'", Integer.class);
        assertThat(singleAdminIndexCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Database engine constraint uq_user_roles_single_admin permits at most one ADMIN")
    void singleAdminConstraintEnforced() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                user1, "admin1-" + user1 + "@example.com", "hash1", "Admin One", true);
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                user2, "admin2-" + user2 + "@example.com", "hash2", "Admin Two", true);

        // First ADMIN assignment succeeds
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'ADMIN')", user1);

        // Second ADMIN assignment fails immediately with unique constraint violation
        assertThatThrownBy(() -> {
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'ADMIN')", user2);
        }).isInstanceOf(DataIntegrityViolationException.class);

        // Multiple DISPATCHER assignments succeed (partial index only restricts ADMIN)
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'DISPATCHER')", user1);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'DISPATCHER')", user2);

        // Cleanup test data
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (?, ?)", user1, user2);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", user1, user2);
    }

    @Test
    @DisplayName("Existing database migration preserves Admin identity and deactivates all legacy dispatchers")
    void existingDatabaseMigrationPreservesAdminAndDeactivatesAllLegacyDispatchers() {
        // Temporarily allow DISPATCHER_ADMIN in roles to simulate pre-migration state
        jdbcTemplate.update("INSERT INTO roles (name) VALUES ('DISPATCHER_ADMIN') ON CONFLICT DO NOTHING");

        UUID adminId = UUID.randomUUID();
        UUID disp1Id = UUID.randomUUID();
        UUID disp2Id = UUID.randomUUID();
        UUID custId = UUID.randomUUID();

        // 1. Setup pre-migration users
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                adminId, "admin@swiftdrop.local", "$2a$10$originalPasswordHashValue123", "System Administrator", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'DISPATCHER_ADMIN')", adminId);

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                disp1Id, "disp1@gmail.com", "$2a$10$hashDisp1", "Dispatcher 01", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'DISPATCHER_ADMIN')", disp1Id);

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                disp2Id, "disp2@gmail.com", "$2a$10$hashDisp2", "Dispatcher 02", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'DISPATCHER_ADMIN')", disp2Id);

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                custId, "customer@gmail.com", "$2a$10$hashCust", "Customer User", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CUSTOMER')", custId);

        // 2. Execute migration logic
        executeMigrationBlock();

        // 3. Verify Admin preservation
        Map<String, Object> adminRow = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", adminId);
        assertThat(adminRow.get("id")).isEqualTo(adminId);
        assertThat(adminRow.get("email")).isEqualTo("admin@swiftdrop.local");
        assertThat(adminRow.get("password_hash")).isEqualTo("$2a$10$originalPasswordHashValue123");
        assertThat(adminRow.get("full_name")).isEqualTo("System Administrator");
        assertThat(adminRow.get("enabled")).isEqualTo(true);

        List<String> adminRoles = jdbcTemplate.queryForList(
                "SELECT role_name FROM user_roles WHERE user_id = ?", String.class, adminId);
        assertThat(adminRoles).containsExactly("ADMIN");

        // 4. Verify all non-Admin legacy dispatchers are deactivated and have roles removed
        for (UUID dispId : List.of(disp1Id, disp2Id)) {
            Map<String, Object> dispRow = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", dispId);
            assertThat(dispRow.get("enabled")).isEqualTo(false); // Deactivated

            List<String> dispRoles = jdbcTemplate.queryForList(
                "SELECT role_name FROM user_roles WHERE user_id = ?", String.class, dispId);
            assertThat(dispRoles).isEmpty(); // Obsolete roles removed
        }

        // 5. Verify customer account is untouched
        Map<String, Object> custRow = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", custId);
        assertThat(custRow.get("enabled")).isEqualTo(true);
        List<String> custRoles = jdbcTemplate.queryForList(
                "SELECT role_name FROM user_roles WHERE user_id = ?", String.class, custId);
        assertThat(custRoles).containsExactly("CUSTOMER");

        // Cleanup
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (?, ?, ?, ?)", adminId, disp1Id, disp2Id, custId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)", adminId, disp1Id, disp2Id, custId);
    }

    @Test
    @DisplayName("Migration aborts and rolls back when Admin account is missing or disabled")
    void migrationFailsClosedWhenAdminIsMissingOrDisabled() {
        jdbcTemplate.update("INSERT INTO roles (name) VALUES ('DISPATCHER_ADMIN') ON CONFLICT DO NOTHING");

        UUID dispId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                dispId, "disp-only@gmail.com", "hash", "Dispatcher Only", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'DISPATCHER_ADMIN')", dispId);

        // Attempting migration when admin@swiftdrop.local does not exist must fail closed
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                executeMigrationBlock();
                return null;
            });
        }).isInstanceOf(DataAccessException.class)
          .hasMessageContaining("Expected exactly 1 account with email admin@swiftdrop.local");

        // Verify rollback: legacy dispatcher still has DISPATCHER_ADMIN role and enabled = true
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", dispId);
        assertThat(row.get("enabled")).isEqualTo(true);

        List<String> roles = jdbcTemplate.queryForList(
                "SELECT role_name FROM user_roles WHERE user_id = ?", String.class, dispId);
        assertThat(roles).containsExactly("DISPATCHER_ADMIN");

        // Cleanup
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", dispId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", dispId);
    }

    @Test
    @DisplayName("Historical parcel_status_history preserves DISPATCHER_ADMIN records")
    void historicalParcelAuditRecordsPreserved() {
        UUID parcelId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                actorId, "historical-actor-" + actorId + "@example.com", "hash", "Historical Actor", true);

        // Insert parcel
        UUID zoneId = UUID.randomUUID();
        UUID pricingRuleId = UUID.randomUUID();
        String validTrackingNumber = "SD" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        validTrackingNumber = validTrackingNumber.replaceAll("[ILOU]", "A"); // Ensure valid Crockford base32

        jdbcTemplate.update(
                "INSERT INTO parcels (id, tracking_number, source, customer_id, pickup_contact_name, pickup_phone, pickup_line1, pickup_city, "
                        + "pickup_zone_id, pickup_postal_code, dropoff_contact_name, dropoff_phone, dropoff_line1, dropoff_city, "
                        + "dropoff_zone_id, dropoff_postal_code, size, weight_grams, fee_amount, fee_currency, pricing_rule_id, "
                        + "status, last_sequence, created_at, updated_at, version) "
                        + "VALUES (?, ?, 'CUSTOMER', ?, 'Contact', '0771234567', 'Line 1', 'Colombo', ?, '00100', 'Dropoff', '0777654321', "
                        + "'Drop Line', 'Kandy', ?, '20000', 'SMALL', 1000, 500.00, 'LKR', ?, 'CREATED', 1, NOW(), NOW(), 0)",
                parcelId, validTrackingNumber, actorId, zoneId, zoneId, pricingRuleId);

        // Insert status history with DISPATCHER_ADMIN
        UUID eventId1 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO parcel_status_history (id, parcel_id, sequence, from_status, to_status, actor_user_id, actor_role, event_id, occurred_at) "
                        + "VALUES (?, ?, 1, NULL, 'CREATED', ?, 'DISPATCHER_ADMIN', ?, NOW())",
                UUID.randomUUID(), parcelId, actorId, eventId1);

        // Insert new status history with ADMIN
        UUID eventId2 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO parcel_status_history (id, parcel_id, sequence, from_status, to_status, actor_user_id, actor_role, event_id, occurred_at) "
                        + "VALUES (?, ?, 2, 'CREATED', 'PICKED_UP', ?, 'ADMIN', ?, NOW())",
                UUID.randomUUID(), parcelId, actorId, eventId2);

        // Insert new status history with DISPATCHER
        UUID eventId3 = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO parcel_status_history (id, parcel_id, sequence, from_status, to_status, actor_user_id, actor_role, event_id, occurred_at) "
                        + "VALUES (?, ?, 3, 'PICKED_UP', 'IN_TRANSIT', ?, 'DISPATCHER', ?, NOW())",
                UUID.randomUUID(), parcelId, actorId, eventId3);

        List<String> recordedRoles = jdbcTemplate.queryForList(
                "SELECT actor_role FROM parcel_status_history WHERE parcel_id = ? ORDER BY sequence",
                String.class, parcelId);

        assertThat(recordedRoles).containsExactly("DISPATCHER_ADMIN", "ADMIN", "DISPATCHER");

        // Cleanup
        jdbcTemplate.update("DELETE FROM parcel_status_history WHERE parcel_id = ?", parcelId);
        jdbcTemplate.update("DELETE FROM parcels WHERE id = ?", parcelId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
    }

    private void executeMigrationBlock() {
        jdbcTemplate.execute("""
            DO $$
            DECLARE
                v_admin_id UUID;
                v_admin_count INT;
                v_user_count INT;
                v_admin_enabled BOOLEAN;
                v_admin_has_role BOOLEAN;
            BEGIN
                SELECT COUNT(*) INTO v_user_count FROM users;

                IF v_user_count > 0 THEN
                    SELECT COUNT(*) INTO v_admin_count 
                    FROM users 
                    WHERE email = 'admin@swiftdrop.local';

                    IF v_admin_count != 1 THEN
                        RAISE EXCEPTION 'Migration halted: Expected exactly 1 account with email admin@swiftdrop.local, found %', v_admin_count;
                    END IF;

                    SELECT id, enabled INTO v_admin_id, v_admin_enabled 
                    FROM users 
                    WHERE email = 'admin@swiftdrop.local';

                    IF NOT v_admin_enabled THEN
                        RAISE EXCEPTION 'Migration halted: Existing Admin account admin@swiftdrop.local is disabled.';
                    END IF;

                    SELECT EXISTS(
                        SELECT 1 FROM user_roles 
                        WHERE user_id = v_admin_id AND role_name = 'DISPATCHER_ADMIN'
                    ) INTO v_admin_has_role;

                    IF NOT v_admin_has_role THEN
                        RAISE EXCEPTION 'Migration halted: Existing Admin account admin@swiftdrop.local does not hold DISPATCHER_ADMIN role.';
                    END IF;

                    UPDATE user_roles 
                    SET role_name = 'ADMIN' 
                    WHERE user_id = v_admin_id AND role_name = 'DISPATCHER_ADMIN';

                    UPDATE users 
                    SET enabled = FALSE 
                    WHERE id IN (
                        SELECT ur.user_id 
                        FROM user_roles ur 
                        WHERE ur.role_name = 'DISPATCHER_ADMIN' 
                          AND ur.user_id != v_admin_id
                    );

                    DELETE FROM user_roles 
                    WHERE role_name = 'DISPATCHER_ADMIN' 
                      AND user_id != v_admin_id;

                END IF;
            END $$;
        """);
    }
}
