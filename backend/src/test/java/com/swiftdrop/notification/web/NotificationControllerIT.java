package com.swiftdrop.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationDeliveryStatus;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.notification.domain.NotificationRecordRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.test.security.TestPrincipal;

@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.notification.reconciliation.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class NotificationControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRecordRepository recordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ParcelEvents parcelEvents;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private final UUID userAlice = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private final UUID userBob = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM notification_records");
        jdbcTemplate.execute("DELETE FROM notification_pending_events");
        jdbcTemplate.execute("DELETE FROM notification_consumer_state");
        jdbcTemplate.execute("DELETE FROM user_roles");
        jdbcTemplate.execute("DELETE FROM users");

        // Seed Alice
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                userAlice, "alice@example.com", "hash", "Alice", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CUSTOMER')", userAlice);

        // Seed Bob
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                userBob, "bob@example.com", "hash", "Bob", true);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'CUSTOMER')", userBob);
    }

    private NotificationRecord insertRecord(UUID recipient, String tracking, NotificationChannel channel, boolean isRead, Instant createdAt) {
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID(),
                recipient,
                UUID.randomUUID(),
                tracking,
                "Title for " + tracking,
                "Message for " + tracking,
                channel,
                NotificationDeliveryStatus.PENDING,
                createdAt);
        if (isRead) {
            record.markAsRead(createdAt.plusSeconds(60));
        }
        return recordRepository.save(record);
    }

    @Test
    @DisplayName("GET /api/v1/notifications returns user's IN_APP feed ordered createdAt DESC, id DESC with stable pagination")
    void getFeedReturnsOrderedInAppNotifications() throws Exception {
        Instant t1 = Instant.parse("2026-11-13T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-13T10:05:00Z");
        Instant t3 = Instant.parse("2026-11-13T10:10:00Z");

        // Alice's records
        NotificationRecord r1 = insertRecord(userAlice, "SD0000000001", NotificationChannel.IN_APP, false, t1);
        NotificationRecord r2 = insertRecord(userAlice, "SD0000000002", NotificationChannel.IN_APP, true, t2);
        NotificationRecord r3 = insertRecord(userAlice, "SD0000000003", NotificationChannel.IN_APP, false, t3);

        // Non-in-app channel and Bob's record (should never appear in Alice's feed)
        insertRecord(userAlice, "SD0000000004", NotificationChannel.SMS, false, t3.plusSeconds(5));
        insertRecord(userBob, "SD0000000005", NotificationChannel.IN_APP, false, t3.plusSeconds(10));

        // Page 0, size 2 -> should return r3, then r2
        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "0")
                        .param("size", "2")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(r3.getId().toString()))
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD0000000003"))
                .andExpect(jsonPath("$.items[1].id").value(r2.getId().toString()))
                .andExpect(jsonPath("$.items[1].trackingNumber").value("SD0000000002"));

        // Page 1, size 2 -> should return r1
        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "1")
                        .param("size", "2")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(r1.getId().toString()))
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD0000000001"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications with unreadOnly=true filters out read records")
    void getFeedWithUnreadOnly() throws Exception {
        Instant now = Instant.parse("2026-11-13T12:00:00Z");
        NotificationRecord unread1 = insertRecord(userAlice, "SD0000000011", NotificationChannel.IN_APP, false, now.minusSeconds(20));
        insertRecord(userAlice, "SD0000000012", NotificationChannel.IN_APP, true, now.minusSeconds(10));
        NotificationRecord unread2 = insertRecord(userAlice, "SD0000000013", NotificationChannel.IN_APP, false, now);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("unreadOnly", "true")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(unread2.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(unread1.getId().toString()));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count returns accurate count of IN_APP unread notifications")
    void getUnreadCountReturnsCount() throws Exception {
        Instant now = Instant.parse("2026-11-13T12:00:00Z");
        insertRecord(userAlice, "SD0000000021", NotificationChannel.IN_APP, false, now);
        insertRecord(userAlice, "SD0000000022", NotificationChannel.IN_APP, false, now);
        insertRecord(userAlice, "SD0000000023", NotificationChannel.IN_APP, true, now);
        insertRecord(userAlice, "SD0000000024", NotificationChannel.EMAIL, false, now);
        insertRecord(userBob, "SD0000000025", NotificationChannel.IN_APP, false, now);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(TestPrincipal.customer(userBob).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read idempotently marks a notification as read and returns 404 for cross-user")
    void markAsReadLifecycle() throws Exception {
        Instant now = Instant.parse("2026-11-13T12:00:00Z");
        NotificationRecord record = insertRecord(userAlice, "SD0000000031", NotificationChannel.IN_APP, false, now);

        // 1. Mark as read by Alice
        mockMvc.perform(patch("/api/v1/notifications/" + record.getId() + "/read")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(record.getId().toString()))
                .andExpect(jsonPath("$.isRead").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        NotificationRecord updated = recordRepository.findById(record.getId()).orElseThrow();
        assertThat(updated.isRead()).isTrue();
        Instant firstReadAt = updated.getReadAt();

        // 2. Mark as read again (idempotent: does not update readAt timestamp)
        mockMvc.perform(patch("/api/v1/notifications/" + record.getId() + "/read")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        NotificationRecord secondFetch = recordRepository.findById(record.getId()).orElseThrow();
        assertThat(secondFetch.getReadAt()).isEqualTo(firstReadAt);

        // 3. Bob attempts to mark Alice's notification as read -> 404 Not Found (prevents enumeration)
        mockMvc.perform(patch("/api/v1/notifications/" + record.getId() + "/read")
                        .with(TestPrincipal.customer(userBob).postProcessor()))
                .andExpect(status().isNotFound());

        // 4. Non-existent UUID -> 404 Not Found
        mockMvc.perform(patch("/api/v1/notifications/" + UUID.randomUUID() + "/read")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/notifications/read-all marks all unread IN_APP records and preserves existing readAt")
    void markAllAsReadBulk() throws Exception {
        Instant t0 = Instant.parse("2026-11-13T10:00:00Z");
        Instant tRead = Instant.parse("2026-11-13T10:30:00Z");

        NotificationRecord alreadyRead = insertRecord(userAlice, "SD0000000041", NotificationChannel.IN_APP, false, t0);
        alreadyRead.markAsRead(tRead);
        recordRepository.save(alreadyRead);

        NotificationRecord unread1 = insertRecord(userAlice, "SD0000000042", NotificationChannel.IN_APP, false, t0.plusSeconds(10));
        NotificationRecord unread2 = insertRecord(userAlice, "SD0000000043", NotificationChannel.IN_APP, false, t0.plusSeconds(20));
        NotificationRecord bobUnread = insertRecord(userBob, "SD0000000044", NotificationChannel.IN_APP, false, t0.plusSeconds(30));

        // Execute bulk read
        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));

        // Verify unread1 and unread2 are marked as read
        NotificationRecord fetched1 = recordRepository.findById(unread1.getId()).orElseThrow();
        NotificationRecord fetched2 = recordRepository.findById(unread2.getId()).orElseThrow();
        assertThat(fetched1.isRead()).isTrue();
        assertThat(fetched1.getReadAt()).isNotNull();
        assertThat(fetched2.isRead()).isTrue();
        assertThat(fetched2.getReadAt()).isNotNull();

        // Verify alreadyRead still has original readAt timestamp
        NotificationRecord fetchedAlreadyRead = recordRepository.findById(alreadyRead.getId()).orElseThrow();
        assertThat(fetchedAlreadyRead.getReadAt()).isEqualTo(tRead);

        // Verify Bob's notification is unaffected
        NotificationRecord fetchedBob = recordRepository.findById(bobUnread.getId()).orElseThrow();
        assertThat(fetchedBob.isRead()).isFalse();

        // Repeated POST /read-all returns updatedCount = 0
        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .with(TestPrincipal.customer(userAlice).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(0));
    }
}
