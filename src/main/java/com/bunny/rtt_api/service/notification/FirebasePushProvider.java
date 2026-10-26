package com.bunny.rtt_api.service.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "app.push", havingValue = "firebase")
public class FirebasePushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushProvider.class);
    private boolean initialized = false;

    public FirebasePushProvider() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
            log.info("FirebasePushProvider initialized successfully");
        } catch (IOException e) {
            log.warn("FirebasePushProvider could not initialize with application default credentials: {}", e.getMessage());
        }
    }

    @Override
    public void sendPush(String targetToken, String title, String body) {
        if (!initialized || targetToken == null || targetToken.isBlank()) {
            log.debug("Push skipped (initialized={}, targetToken={})", initialized, targetToken);
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(targetToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent Firebase push message: {}", response);
        } catch (Exception e) {
            log.error("Failed to send Firebase push to token {}: {}", targetToken, e.getMessage());
        }
    }

    @Override
    public String name() {
        return "firebase";
    }
}
