package com.swiftdrop.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.swiftdrop.notification.application.MockPushNotificationSender;
import com.swiftdrop.notification.application.PushNotificationSender.PushSendResult;

class FcmPushNotificationSenderTest {

    @Test
    @DisplayName("MockPushNotificationSender is the default when no push provider is configured")
    void mockProviderIsDefault() {
        ConditionalOnProperty annotation = MockPushNotificationSender.class
                .getAnnotation(ConditionalOnProperty.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).containsExactly("swiftdrop.notification.push.provider");
        assertThat(annotation.havingValue()).isEqualTo("mock");
        assertThat(annotation.matchIfMissing()).isTrue();

        MockPushNotificationSender mockSender = new MockPushNotificationSender();
        PushSendResult result = mockSender.send("token-123", "Title", "Body", Map.of("parcelId", "p1"));
        assertThat(result).isEqualTo(PushSendResult.SENT);
    }

    @Test
    @DisplayName("Fails clearly when FCM is enabled without service account path")
    void rejectsMissingServiceAccountPath() {
        FcmProperties properties = new FcmProperties("  ");
        assertThatThrownBy(() -> new FcmPushNotificationSender(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SWIFTDROP_FIREBASE_SERVICE_ACCOUNT_PATH");
    }

    @Test
    @DisplayName("Sends FCM message successfully with title, body, and data")
    void sendsFcmMessageSuccessfully() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messaging.send(any(Message.class))).thenReturn("projects/test/messages/123");
        FcmPushNotificationSender sender = new FcmPushNotificationSender(messaging);

        PushSendResult result = sender.send(
                "fcm-token-abc",
                "Parcel Booked",
                "Your parcel SD123 has been booked.",
                Map.of("parcelId", "uuid-1", "trackingNumber", "SD123"));

        assertThat(result).isEqualTo(PushSendResult.SENT);
        verify(messaging).send(any(Message.class));
    }

    @Test
    @DisplayName("Returns INVALID_TOKEN when FCM reports UNREGISTERED")
    void returnsInvalidTokenOnUnregistered() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        FirebaseMessagingException unregisteredEx = mock(FirebaseMessagingException.class);
        when(unregisteredEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(unregisteredEx);

        FcmPushNotificationSender sender = new FcmPushNotificationSender(messaging);
        PushSendResult result = sender.send("stale-token", "Title", "Body", Map.of());

        assertThat(result).isEqualTo(PushSendResult.INVALID_TOKEN);
    }

    @Test
    @DisplayName("Throws IllegalStateException on INVALID_ARGUMENT without deactivating token")
    void throwsOnInvalidArgument() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        FirebaseMessagingException invalidArgEx = mock(FirebaseMessagingException.class);
        when(invalidArgEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(messaging.send(any(Message.class))).thenThrow(invalidArgEx);

        FcmPushNotificationSender sender = new FcmPushNotificationSender(messaging);

        assertThatThrownBy(() -> sender.send("token-1", "Title", "Body", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVALID_ARGUMENT");
    }

    @Test
    @DisplayName("Throws IllegalStateException on transient FCM service failure")
    void throwsOnFcmServiceFailure() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        FirebaseMessagingException internalEx = mock(FirebaseMessagingException.class);
        when(internalEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(messaging.send(any(Message.class))).thenThrow(internalEx);

        FcmPushNotificationSender sender = new FcmPushNotificationSender(messaging);

        assertThatThrownBy(() -> sender.send("token-1", "Title", "Body", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL");
    }
}
