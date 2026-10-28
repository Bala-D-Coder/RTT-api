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
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class GeminiLiveClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLiveClient.class);
    // Both v1alpha and v1beta support the live models — use v1beta (stable)
    private static final String GEMINI_LIVE_WS_BASE = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=";

    private final String maskedUrl;
    private final URI targetUri;
    private final String model;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private WebSocket webSocket;

    private volatile boolean setupComplete = false;
    private final Queue<byte[]> pendingAudio = new ConcurrentLinkedQueue<>();
    private final Queue<String> outboundQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private long audioChunkCount = 0;

    public interface Listener {
        default void onInterim(String text) {
            onTextDelta(text);
        }

        default void onFinalSegment(String text) {}

        void onTextDelta(String text);

        void onTurnComplete();

        void onError(Throwable error);

        void onClose(int statusCode, String reason);
    }

    public GeminiLiveClient(String apiKey, String model, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // gemini-3.5-transcribe-live = dedicated speech-to-text only Live model (pure ASR, no audio output)
        this.model = (model != null && !model.isBlank()) ? model : "models/gemini-3.5-transcribe-live";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        // Masked URL rule: Never log the URI containing the API key
        this.maskedUrl = "wss://generativelanguage.googleapis.com/.../BidiGenerateContent?key=***";
        this.targetUri = URI.create(GEMINI_LIVE_WS_BASE + (apiKey != null ? apiKey : ""));
    }

    public CompletableFuture<Void> connect(Listener listener) {
        log.info("Connecting to Gemini Live at {}", maskedUrl);
        this.setupComplete = false;
        this.pendingAudio.clear();
        this.outboundQueue.clear();
        this.isSending.set(false);
        this.audioChunkCount = 0;

        WebSocket.Listener wsListener = new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) {
                log.info("Gemini Live WebSocket opened");
                GeminiLiveClient.this.webSocket = ws;
                sendSetupMessage();
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
            public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                buffer.append(text);
                if (last) {
                    String fullMessage = buffer.toString();
                    buffer.setLength(0);
                    log.info("Gemini raw message: {}", fullMessage);
                    processIncomingMessage(fullMessage, listener);
                }
                return WebSocket.Listener.super.onBinary(ws, data, last);
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

    private void sendSetupMessage() {
        try {
            Map<String, Object> setupPayload;
            if (model.contains("transcribe")) {
                // gemini-3.5-transcribe-live: pure ASR model.
                // generationConfig ensures text modalities are immediately streamed
                setupPayload = Map.of(
                        "setup", Map.of(
                                "model", model,
                                "generationConfig", Map.of(
                                        "responseModalities", new String[] { "TEXT" }),
                                "systemInstruction", Map.of(
                                        "parts", new Object[] {
                                                Map.of("text",
                                                        "You are a real-time speech transcription engine. "
                                                        + "Transcribe exactly what is spoken in English, Hindi, or Hinglish. "
                                                        + "Preserve the spoken language and code-switching as-is. "
                                                        + "Do not translate, summarize, or explain. "
                                                        + "Output only the spoken words.")
                                        })));
            } else {
                // Full live models — TEXT output only, no audio TTS.
                setupPayload = Map.of(
                        "setup", Map.of(
                                "model", model,
                                "generationConfig", Map.of(
                                        "responseModalities", new String[] { "TEXT" })));
            }
            String json = objectMapper.writeValueAsString(setupPayload);
            log.info("Sending Gemini Live setup with model: {}", model);
            enqueueSend(json);
        } catch (Exception e) {
            log.error("Failed to send Gemini setup message", e);
        }
    }

    public void sendAudioChunk(ByteBuffer pcm16) {
        if (pcm16 == null || pcm16.remaining() == 0) {
            return;
        }

        byte[] bytes = new byte[pcm16.remaining()];
        pcm16.get(bytes);

        // Compute peak amplitude for diagnostic detection of dead microphone
        int peak = 0;
        for (int i = 0; i < bytes.length - 1; i += 2) {
            short sample = (short) ((bytes[i] & 0xFF) | (bytes[i + 1] << 8));
            int abs = Math.abs(sample);
            if (abs > peak) peak = abs;
        }

        if (!setupComplete || webSocket == null || webSocket.isOutputClosed()) {
            if (pendingAudio.size() < 100) {
                pendingAudio.offer(bytes);
            }
            return;
        }

        while (!pendingAudio.isEmpty()) {
            byte[] queued = pendingAudio.poll();
            if (queued != null) {
                sendBase64Chunk(queued, peak);
            }
        }

        sendBase64Chunk(bytes, peak);
    }

    private void sendBase64Chunk(byte[] bytes, int peak) {
        audioChunkCount++;
        if (audioChunkCount == 1 || audioChunkCount % 40 == 0) {
            log.info("Forwarded audio chunk #{} to Gemini Live (peak: {} / 32767)", audioChunkCount, peak);
        }
        try {
            String base64Audio = Base64.getEncoder().encodeToString(bytes);
            Map<String, Object> realtimeInput = Map.of(
                    "realtimeInput", Map.of(
                            "mediaChunks", new Object[] {
                                    Map.of(
                                            "mimeType", "audio/pcm;rate=16000",
                                            "data", base64Audio)
                            }));
            String json = objectMapper.writeValueAsString(realtimeInput);
            enqueueSend(json);
        } catch (Exception e) {
            log.error("Failed to serialize audio chunk for Gemini", e);
        }
    }

    /**
     * java.net.http.WebSocket forbids initiating a new sendText while the previous sendText is in-flight.
     * Attempting to send concurrently immediately completes the CompletableFuture exceptionally with
     * IllegalStateException and silently drops the frame.
     * This queue ensures all messages and audio frames are strictly sequenced.
     */
    private void enqueueSend(String json) {
        if (webSocket == null || webSocket.isOutputClosed()) {
            return;
        }
        outboundQueue.offer(json);
        drainOutboundQueue();
    }

    private void drainOutboundQueue() {
        if (!isSending.compareAndSet(false, true)) {
            return;
        }
        sendNext();
    }

    private void sendNext() {
        WebSocket ws = this.webSocket;
        if (ws == null || ws.isOutputClosed()) {
            outboundQueue.clear();
            isSending.set(false);
            return;
        }

        String msg = outboundQueue.poll();
        if (msg == null) {
            isSending.set(false);
            if (!outboundQueue.isEmpty() && isSending.compareAndSet(false, true)) {
                sendNext();
            }
            return;
        }

        ws.sendText(msg, true).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to deliver message to Gemini Live WebSocket: {}", ex.getMessage(), ex);
            }
            sendNext();
        });
    }

    public void flush() {
        try {
            Map<String, Object> turnComplete = Map.of(
                    "clientContent", Map.of(
                            "turns", new Object[] {},
                            "turnComplete", true
                    )
            );
            String json = objectMapper.writeValueAsString(turnComplete);
            log.info("Sending turnComplete flush to Gemini Live");
            enqueueSend(json);
        } catch (Exception e) {
            log.warn("Failed to send turnComplete to Gemini: {}", e.getMessage());
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

            // Log errors if returned by Gemini
            if (root.has("error")) {
                log.error("Gemini returned error payload: {}", root.path("error"));
            }

            // Log voiceActivity detections from Gemini
            if (root.has("voiceActivity")) {
                log.info("Gemini voiceActivity: {}", root.path("voiceActivity"));
            }

            // ── Setup complete ────────────────────────────────────────────────────────
            if (root.has("setupComplete")) {
                log.info("Gemini Live setup acknowledged. Ready for audio ingestion.");
                this.setupComplete = true;
                while (!pendingAudio.isEmpty()) {
                    byte[] queued = pendingAudio.poll();
                    if (queued != null) {
                        sendBase64Chunk(queued, 0);
                    }
                }
                return;
            }

            JsonNode serverContent = root.path("serverContent");

            // ── Dedicated transcription model: gemini-3.5-transcribe-live ─────────────
            // Partials arrive as interimInputTranscription, finals as inputTranscription
            if (serverContent.has("interimInputTranscription")) {
                String text = serverContent.path("interimInputTranscription").path("text").asText("");
                if (!text.isBlank()) {
                    log.info("Gemini interim transcript: '{}'", text);
                    listener.onInterim(text);
                }
            }
            if (serverContent.has("inputTranscription")) {
                String text = serverContent.path("inputTranscription").path("text").asText("");
                if (!text.isBlank()) {
                    log.info("Gemini final transcript: '{}'", text);
                    listener.onFinalSegment(text);
                }
            }

            // ── Full live models: modelTurn text deltas ───────────────────────────────
            if (serverContent.has("modelTurn")) {
                JsonNode parts = serverContent.path("modelTurn").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        if (part.has("text")) {
                            listener.onTextDelta(part.get("text").asText());
                        }
                    }
                }
            }

            if (serverContent.path("turnComplete").asBoolean(false)) {
                listener.onTurnComplete();
            }
        } catch (Exception e) {
            log.error("Error parsing Gemini message: {}", json, e);
        }
    }
}
