package com.swiftdrop.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.notification.application.PushTokenService;
import com.swiftdrop.notification.domain.NotificationPushToken;
import com.swiftdrop.notification.domain.NotificationPushTokenRepository;

class PushTokenControllerTest {

    @Nested
    @DisplayName("PushTokenService unit tests")
    class ServiceTests {

        private NotificationPushTokenRepository repository;
        private PushTokenService service;

        @BeforeEach
        void setUp() {
            repository = mock(NotificationPushTokenRepository.class);
            service = new PushTokenService(repository);
        }

        @Test
        @DisplayName("Registers a new token for the authenticated user")
        void registersNewToken() {
            UUID userId = UUID.randomUUID();
            when(repository.findByToken("fcm-token-1")).thenReturn(Optional.empty());
            when(repository.save(any(NotificationPushToken.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            NotificationPushToken saved = service.registerToken(userId, "fcm-token-1", "WEB");

            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getToken()).isEqualTo("fcm-token-1");
            assertThat(saved.getPlatform()).isEqualTo("WEB");
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("Registering the same token twice updates existing record without duplicating")
        void registersSameTokenTwice() {
            UUID userId = UUID.randomUUID();
            NotificationPushToken existing = new NotificationPushToken(
                    userId, "fcm-token-1", "WEB", Instant.parse("2026-11-16T08:00:00Z"));
            existing.deactivate(Instant.parse("2026-11-16T08:05:00Z"));

            when(repository.findByToken("fcm-token-1")).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            NotificationPushToken saved = service.registerToken(userId, "fcm-token-1", "WEB");

            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.isActive()).isTrue();
            verify(repository).save(existing);
        }

        @Test
        @DisplayName("Reassigns an existing token to another authenticated user")
        void reassignsTokenToAnotherUser() {
            UUID firstUser = UUID.randomUUID();
            UUID secondUser = UUID.randomUUID();
            NotificationPushToken existing = new NotificationPushToken(
                    firstUser, "fcm-token-shared", "WEB", Instant.parse("2026-11-16T08:00:00Z"));

            when(repository.findByToken("fcm-token-shared")).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            NotificationPushToken saved = service.registerToken(secondUser, "fcm-token-shared", "WEB");

            assertThat(saved.getUserId()).isEqualTo(secondUser);
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("Unregisters only the matching token belonging to the authenticated user")
        void unregistersOwnedTokenOnly() {
            UUID owner = UUID.randomUUID();
            UUID otherUser = UUID.randomUUID();

            when(repository.deactivateTokenForUser(eq("fcm-token-1"), eq(owner), any(Instant.class)))
                    .thenReturn(1);
            when(repository.deactivateTokenForUser(eq("fcm-token-1"), eq(otherUser), any(Instant.class)))
                    .thenReturn(0);

            assertThat(service.unregisterToken(owner, "fcm-token-1")).isTrue();
            assertThat(service.unregisterToken(otherUser, "fcm-token-1")).isFalse();
        }
    }

    @Nested
    @WebMvcTest(PushTokenController.class)
    @DisplayName("PushTokenController web tests")
    class WebTests {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private PushTokenService pushTokenService;

        @MockitoBean
        private NotificationPushTokenRepository pushTokenRepository;

        @Test
        @DisplayName("POST /api/v1/notifications/push-tokens registers token for authenticated user")
        void postRegistersToken() throws Exception {
            UUID userId = UUID.randomUUID();
            NotificationPushToken saved = new NotificationPushToken(
                    userId, "fcm-token-1", "WEB", Instant.parse("2026-11-16T08:00:00Z"));
            when(pushTokenService.registerToken(userId, "fcm-token-1", "WEB")).thenReturn(saved);

            mockMvc.perform(post("/api/v1/notifications/push-tokens")
                            .with(jwt().jwt(j -> j.subject(userId.toString())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"fcm-token-1\",\"platform\":\"WEB\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.platform").value("WEB"))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }

        @Test
        @DisplayName("DELETE /api/v1/notifications/push-tokens deactivates token for authenticated user")
        void deleteUnregistersToken() throws Exception {
            UUID userId = UUID.randomUUID();
            when(pushTokenService.unregisterToken(userId, "fcm-token-1")).thenReturn(true);

            mockMvc.perform(delete("/api/v1/notifications/push-tokens")
                            .with(jwt().jwt(j -> j.subject(userId.toString())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"fcm-token-1\"}"))
                    .andExpect(status().isNoContent());

            verify(pushTokenService).unregisterToken(userId, "fcm-token-1");
        }

        @Test
        @DisplayName("Rejects unauthenticated requests")
        void rejectsUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/notifications/push-tokens")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"fcm-token-1\",\"platform\":\"WEB\"}"))
                    .andExpect(status().is4xxClientError());

            mockMvc.perform(delete("/api/v1/notifications/push-tokens")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"fcm-token-1\"}"))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Rejects empty token input with 400 Bad Request")
        void rejectsInvalidToken() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/notifications/push-tokens")
                            .with(jwt().jwt(j -> j.subject(userId.toString())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"  \",\"platform\":\"WEB\"}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(delete("/api/v1/notifications/push-tokens")
                            .with(jwt().jwt(j -> j.subject(userId.toString())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
