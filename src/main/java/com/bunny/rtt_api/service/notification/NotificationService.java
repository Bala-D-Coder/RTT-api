package com.bunny.rtt_api.service.notification;

import com.bunny.rtt_api.dto.realtime.WsNotificationEvent;
import com.bunny.rtt_api.entity.Reminder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final PushProvider pushProvider;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> activeSessions = ConcurrentHashMap.newKeySet();

    public NotificationService(PushProvider pushProvider, ObjectMapper objectMapper) {
        this.pushProvider = pushProvider;
        this.objectMapper = objectMapper;
    }

    public void registerSession(WebSocketSession session) {
        activeSessions.add(session);
        log.info("Registered notification session: id={} (total: {})", session.getId(), activeSessions.size());
    }

    public void unregisterSession(WebSocketSession session) {
        activeSessions.remove(session);
        log.info("Unregistered notification session: id={} (total: {})", session.getId(), activeSessions.size());
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public void broadcast(WsNotificationEvent event) {
        if (activeSessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : activeSessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try {
                            session.sendMessage(message);
                        } catch (IOException e) {
                            log.error("Failed to send notification to session {}", session.getId(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize notification event: {}", event, e);
        }
    }

    public void notifyReminderDue(Reminder reminder) {
        String reminderId = reminder.getId().toString();
        String noteId = reminder.getNote() != null ? reminder.getNote().getId().toString() : null;
        String title = reminder.getTitle();

        // 1. Broadcast over WebSocket room (cross-tab toasts, B21)
        broadcast(WsNotificationEvent.reminderDue(reminderId, title, noteId));

        // 2. Out-of-band FCM push to created_by user
        pushProvider.sendPush(reminder.getCreatedBy(), "Reminder Due: " + title, "Your reminder is now due");
    }

    public void notifyNoteUpdated(UUID noteId) {
        broadcast(WsNotificationEvent.noteUpdated(noteId.toString()));
    }
}
