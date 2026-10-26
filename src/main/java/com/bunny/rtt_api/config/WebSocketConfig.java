package com.bunny.rtt_api.config;

import com.bunny.rtt_api.service.notification.NotificationWebSocketHandler;
import com.bunny.rtt_api.service.realtime.TranscribeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TranscribeWebSocketHandler transcribeWebSocketHandler;
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final AppProps appProps;

    public WebSocketConfig(
            TranscribeWebSocketHandler transcribeWebSocketHandler,
            NotificationWebSocketHandler notificationWebSocketHandler,
            AppProps appProps
    ) {
        this.transcribeWebSocketHandler = transcribeWebSocketHandler;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.appProps = appProps;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = appProps.cors().allowedOrigins().toArray(new String[0]);

        registry.addHandler(transcribeWebSocketHandler, "/ws/transcribe")
                .setAllowedOrigins(origins);

        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .setAllowedOrigins(origins);
    }
}
