package com.swiftdrop.notification.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.swiftdrop.notification.application.NotificationQueryService;
import com.swiftdrop.notification.domain.NotificationChannel;
import com.swiftdrop.notification.domain.NotificationRecord;
import com.swiftdrop.test.security.TestPrincipal;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@AutoConfigureMockMvc
@MockitoBean(types = {
        com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.delivery.domain.ParcelRepository.class,
        com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository.class,
        com.swiftdrop.delivery.domain.DeliveryProofRepository.class,
        com.swiftdrop.delivery.domain.OutboxEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantRepository.class,
        com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository.class,
        com.swiftdrop.identity.domain.UserRepository.class,
        com.swiftdrop.identity.domain.ExternalIdentityRepository.class,
        com.swiftdrop.identity.domain.GoogleLoginCodeRepository.class,
        com.swiftdrop.pricing.domain.PricingRuleRepository.class,
        com.swiftdrop.pricing.domain.ZoneRepository.class,
        com.swiftdrop.pricing.api.FeeCalculator.class,
        com.swiftdrop.delivery.api.ParcelEvents.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class,
        com.swiftdrop.notification.domain.NotificationConsumerStateRepository.class,
        com.swiftdrop.notification.domain.NotificationPendingEventRepository.class
})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationQueryService queryService;

    private final UUID callerId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private NotificationRecord createSampleRecord(UUID id, UUID recipientUserId, boolean isRead) {
        NotificationRecord record = new NotificationRecord(
                UUID.randomUUID(),
                recipientUserId,
                UUID.randomUUID(),
                "SWD-12345678",
                "Parcel Booked",
                "Your parcel SWD-12345678 has been booked.",
                NotificationChannel.IN_APP,
                com.swiftdrop.notification.domain.NotificationDeliveryStatus.SENT,
                Instant.parse("2026-11-13T10:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(record, "id", id);
        if (isRead) {
            record.markAsRead(Instant.parse("2026-11-13T10:30:00Z"));
        }
        return record;
    }

    @Nested
    @DisplayName("GET /api/v1/notifications")
    class GetFeedTests {

        @Test
        @DisplayName("Returns 401 when unauthenticated")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/notifications"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Returns paginated feed of notifications for authenticated caller")
        void returnsPaginatedFeed() throws Exception {
            UUID notifId = UUID.randomUUID();
            NotificationRecord record = createSampleRecord(notifId, callerId, false);
            when(queryService.getFeed(eq(callerId), eq(false), eq(0), eq(20)))
                    .thenReturn(new PageImpl<>(List.of(record), org.springframework.data.domain.PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/api/v1/notifications")
                            .with(TestPrincipal.customer(callerId).postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page", equalTo(0)))
                    .andExpect(jsonPath("$.size", equalTo(20)))
                    .andExpect(jsonPath("$.totalItems", equalTo(1)))
                    .andExpect(jsonPath("$.totalPages", equalTo(1)))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].id", equalTo(notifId.toString())))
                    .andExpect(jsonPath("$.items[0].trackingNumber", equalTo("SWD-12345678")))
                    .andExpect(jsonPath("$.items[0].title", equalTo("Parcel Booked")))
                    .andExpect(jsonPath("$.items[0].channel", equalTo("IN_APP")))
                    .andExpect(jsonPath("$.items[0].isRead", equalTo(false)))
                    .andExpect(jsonPath("$.items[0].readAt").doesNotExist());
        }

        @Test
        @DisplayName("Passes unreadOnly=true filter and clamps pagination bounds")
        void passesUnreadOnlyAndClampsBounds() throws Exception {
            when(queryService.getFeed(eq(callerId), eq(true), eq(0), eq(100)))
                    .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 100), 0));

            mockMvc.perform(get("/api/v1/notifications")
                            .param("unreadOnly", "true")
                            .param("page", "-5")
                            .param("size", "500")
                            .with(TestPrincipal.customer(callerId).postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page", equalTo(0)))
                    .andExpect(jsonPath("$.size", equalTo(100)))
                    .andExpect(jsonPath("$.totalItems", equalTo(0)));

            verify(queryService).getFeed(callerId, true, 0, 100);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/notifications/unread-count")
    class GetUnreadCountTests {

        @Test
        @DisplayName("Returns 401 when unauthenticated")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/notifications/unread-count"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Returns unread notification count for caller")
        void returnsUnreadCount() throws Exception {
            when(queryService.getUnreadCount(callerId)).thenReturn(5L);

            mockMvc.perform(get("/api/v1/notifications/unread-count")
                            .with(TestPrincipal.customer(callerId).postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount", equalTo(5)));

            verify(queryService).getUnreadCount(callerId);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/notifications/{id}/read")
    class MarkAsReadTests {

        @Test
        @DisplayName("Returns 401 when unauthenticated")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(patch("/api/v1/notifications/" + UUID.randomUUID() + "/read"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Returns 200 with updated notification DTO on success")
        void markAsReadSuccess() throws Exception {
            UUID notifId = UUID.randomUUID();
            NotificationRecord record = createSampleRecord(notifId, callerId, true);
            when(queryService.markAsRead(notifId, callerId)).thenReturn(record);

            mockMvc.perform(patch("/api/v1/notifications/" + notifId + "/read")
                            .with(TestPrincipal.customer(callerId).postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", equalTo(notifId.toString())))
                    .andExpect(jsonPath("$.isRead", equalTo(true)))
                    .andExpect(jsonPath("$.readAt", equalTo("2026-11-13T10:30:00Z")));

            verify(queryService).markAsRead(notifId, callerId);
        }

        @Test
        @DisplayName("Returns 404 when notification does not exist or belongs to another user")
        void notFoundReturns404() throws Exception {
            UUID notifId = UUID.randomUUID();
            when(queryService.markAsRead(notifId, callerId))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

            mockMvc.perform(patch("/api/v1/notifications/" + notifId + "/read")
                            .with(TestPrincipal.customer(callerId).postProcessor()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/notifications/read-all")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("Returns 401 when unauthenticated")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(post("/api/v1/notifications/read-all"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Marks all notifications as read and returns updated count")
        void markAllAsReadSuccess() throws Exception {
            when(queryService.markAllAsRead(callerId)).thenReturn(3);

            mockMvc.perform(post("/api/v1/notifications/read-all")
                            .with(TestPrincipal.customer(callerId).postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updatedCount", equalTo(3)));

            verify(queryService).markAllAsRead(callerId);
        }
    }
}
