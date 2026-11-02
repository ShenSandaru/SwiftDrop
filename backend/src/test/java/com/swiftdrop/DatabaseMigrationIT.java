package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import com.swiftdrop.pricing.api.FeeCalculator;

/**
 * Verifies that:
 * 1. An isolated PostgreSQL 18 container starts.
 * 2. Flyway applies all migrations from {@code classpath:db/migration}.
 * 3. All identity migrations are recorded in {@code flyway_schema_history}.
 * 4. Hibernate validates all JPA entities against the schema with {@code ddl-auto=validate}.
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

    // Delivery's booking needs pricing, which may not be on the classpath yet
    @MockitoBean
    private FeeCalculator feeCalculator;

    @Test
    void flywayAppliesAllIdentityMigrationsAndHibernateValidationSucceeds() {
        // Assert PostgreSQL container is running
        assertThat(postgres.isRunning()).isTrue();

        // Assert Flyway schema history recorded the three identity and four delivery migrations in order
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT version, description, type, script, success FROM flyway_schema_history ORDER BY installed_rank ASC");

        assertThat(rows).hasSize(7);

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

        assertThat(rows.subList(3, 7))
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

        // Assert Hibernate EntityManager is active and connected
        assertThat(entityManager.isOpen()).isTrue();
    }
}

