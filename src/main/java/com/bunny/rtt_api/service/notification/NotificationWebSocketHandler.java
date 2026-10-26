package com.bunny.rtt_api.service.notification;

import com.bunny.rtt_api.dto.realtime.WsNotificationEvent;
import com.bunny.rtt_api.service.realtime.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    private final TicketService ticketService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationWebSocketHandler(
            TicketService ticketService,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.ticketService = ticketService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        String ticket = extractQueryParam(uri, "ticket");

        if (ticket == null || ticket.isBlank()) {
            log.warn("Notification WebSocket connection rejected: missing ticket");
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing ticket"));
            return;
        }

        try {
            String userId = ticketService.consumeTicket(ticket);
            log.info("Notification WebSocket established for userId={}, sessionId={}", userId, session.getId());
        } catch (Exception e) {
            log.warn("Notification WebSocket rejected: invalid ticket ({})", e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or expired ticket"));
            return;
        }

        notificationService.registerSession(session);

        // Send reconnected event
        sendEvent(session, WsNotificationEvent.reconnected(0));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload().trim();
        if (payload.equalsIgnoreCase("ping") || payload.contains("\"ping\"")) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        notificationService.unregisterSession(session);
        log.info("Notification WebSocket session closed: id={}, status={}", session.getId(), status);
    }

    private synchronized void sendEvent(WebSocketSession session, WsNotificationEvent event) {
        if (!session.isOpen()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send notification event: {}", event, e);
        }
    }

    private String extractQueryParam(URI uri, String paramName) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            String[] parts = pair.split("=");
            if (parts.length >= 2 && parts[0].equalsIgnoreCase(paramName)) {
                return parts[1];
            }
        }
        return null;
    }
}
