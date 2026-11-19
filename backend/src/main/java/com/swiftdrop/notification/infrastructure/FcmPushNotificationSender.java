package com.swiftdrop.notification.infrastructure;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.swiftdrop.notification.application.PushNotificationSender;

/**
 * {@link PushNotificationSender} implementation backed by Firebase Cloud Messaging (FCM).
 * Active only when {@code swiftdrop.notification.push.provider=fcm}.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.notification.push.provider", havingValue = "fcm")
@EnableConfigurationProperties(FcmProperties.class)
public class FcmPushNotificationSender implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushNotificationSender.class);

    private final FirebaseMessaging firebaseMessaging;

    @Autowired
    public FcmPushNotificationSender(FcmProperties properties) {
        this(initializeFirebaseMessaging(properties));
    }

    FcmPushNotificationSender(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = Objects.requireNonNull(
                firebaseMessaging, "firebaseMessaging must not be null");
    }

    @Override
    public PushSendResult send(String token, String title, String body, Map<String, String> data) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null or blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be null or blank");
        }

        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }

        try {
            String messageId = firebaseMessaging.send(messageBuilder.build());
            log.info("Dispatched FCM push notification (messageId={})", messageId);
            return PushSendResult.SENT;
        } catch (FirebaseMessagingException ex) {
            MessagingErrorCode errorCode = ex.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED) {
                log.warn("FCM reported unregistered push token (errorCode={})", errorCode);
                return PushSendResult.INVALID_TOKEN;
            }
            throw new IllegalStateException(
                    "FCM push dispatch failed (errorCode=" + errorCode + ")", ex);
        }
    }

    private static FirebaseMessaging initializeFirebaseMessaging(FcmProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        String serviceAccountPath = properties.serviceAccountPath();
        if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
            throw new IllegalStateException(
                    "FCM push provider is enabled but SWIFTDROP_FIREBASE_SERVICE_ACCOUNT_PATH "
                            + "(swiftdrop.notification.push.fcm.service-account-path) is missing");
        }

        FirebaseApp app;
        synchronized (FcmPushNotificationSender.class) {
            if (!FirebaseApp.getApps().isEmpty()) {
                app = FirebaseApp.getApps().getFirst();
            } else {
                try (InputStream stream = new FileInputStream(serviceAccountPath)) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(stream))
                            .build();
                    app = FirebaseApp.initializeApp(options);
                } catch (IOException ex) {
                    throw new IllegalStateException(
                            "Failed to load Firebase service account credentials from configured path", ex);
                }
            }
        }
        return FirebaseMessaging.getInstance(app);
    }
}
