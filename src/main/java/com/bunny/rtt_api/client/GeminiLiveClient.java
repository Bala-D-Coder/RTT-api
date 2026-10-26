package com.bunny.rtt_api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GeminiLiveClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLiveClient.class);
    private static final String GEMINI_LIVE_WS_BASE = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=";

    private final String maskedUrl;
    private final URI targetUri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private WebSocket webSocket;

    public interface Listener {
        void onTextDelta(String text);

        void onTurnComplete();

        void onError(Throwable error);

        void onClose(int statusCode, String reason);
    }

    public GeminiLiveClient(String apiKey, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        // Masked URL rule: Never log the URI containing the API key
        this.maskedUrl = "wss://generativelanguage.googleapis.com/.../BidiGenerateContent?key=***";
        this.targetUri = URI.create(GEMINI_LIVE_WS_BASE + (apiKey != null ? apiKey : ""));
    }

    public CompletableFuture<Void> connect(Listener listener) {
        log.info("Connecting to Gemini Live at {}", maskedUrl);

        WebSocket.Listener wsListener = new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) {
                log.info("Gemini Live WebSocket opened");
                sendSetupMessage(ws);
                WebSocket.Listener.super.onOpen(ws);
            }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    String fullMessage = buffer.toString();
                    buffer.setLength(0);
                    processIncomingMessage(fullMessage, listener);
                }
                return WebSocket.Listener.super.onText(ws, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                log.info("Gemini Live WebSocket closed: code={}, reason={}", statusCode, reason);
                listener.onClose(statusCode, reason);
                return WebSocket.Listener.super.onClose(ws, statusCode, reason);
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                log.error("Gemini Live WebSocket error", error);
                listener.onError(error);
                WebSocket.Listener.super.onError(ws, error);
            }
        };

        return httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(targetUri, wsListener)
                .thenAccept(ws -> this.webSocket = ws);
    }

    private void sendSetupMessage(WebSocket ws) {
        try {
            Map<String, Object> setup = Map.of(
                    "setup", Map.of(
                            "model", "models/gemini-2.0-flash-exp",
                            "generationConfig", Map.of(
                                    "responseModalities", new String[] { "TEXT" }),
                            "systemInstruction", Map.of(
                                    "parts", new Object[] {
                                            Map.of("text", "You are a real-time verbatim speech-to-text dictation engine. Output ONLY the exact transcribed words spoken by the user. Never converse, answer questions, comment, or summarize.")
                                    })));
            String json = objectMapper.writeValueAsString(setup);
            ws.sendText(json, true);
        } catch (Exception e) {
            log.error("Failed to send Gemini setup message", e);
        }
    }

    public void sendAudioChunk(ByteBuffer pcm16) {
        if (webSocket == null || webSocket.isOutputClosed()) {
            return;
        }

        try {
            byte[] bytes = new byte[pcm16.remaining()];
            pcm16.get(bytes);
            String base64Audio = Base64.getEncoder().encodeToString(bytes);

            Map<String, Object> realtimeInput = Map.of(
                    "realtimeInput", Map.of(
                            "mediaChunks", new Object[] {
                                    Map.of(
                                            "mimeType", "audio/pcm;rate=16000",
                                            "data", base64Audio)
                            }));
            String json = objectMapper.writeValueAsString(realtimeInput);
            webSocket.sendText(json, true);
        } catch (Exception e) {
            log.error("Failed to send audio chunk to Gemini", e);
        }
    }

    public void close() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Session completed");
        }
    }

    private void processIncomingMessage(String json, Listener listener) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode serverContent = root.path("serverContent");
            if (serverContent.has("modelTurn")) {
                JsonNode parts = serverContent.path("modelTurn").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        if (part.has("text")) {
                            String text = part.get("text").asText();
                            listener.onTextDelta(text);
                        }
                    }
                }
            }
            if (serverContent.path("turnComplete").asBoolean(false)) {
                listener.onTurnComplete();
            }
        } catch (Exception e) {
            log.error("Error parsing Gemini message", e);
        }
    }
}
