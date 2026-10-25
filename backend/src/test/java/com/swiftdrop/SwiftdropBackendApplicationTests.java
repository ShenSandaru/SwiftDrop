package com.swiftdrop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks that the application context starts without Docker, PostgreSQL or RabbitMQ.
 * The database auto-configuration is excluded because no database is available here;
 * RabbitMQ connects lazily, so it needs no exclusion.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class SwiftdropBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
