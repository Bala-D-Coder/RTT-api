package com.bunny.rtt_api.service.realtime;

import com.bunny.rtt_api.dto.realtime.WsTranscribeEvent;
import com.bunny.rtt_api.dto.realtime.WsTranscribeStartMessage;
import com.bunny.rtt_api.dto.request.BlockAppendRequest;
import com.bunny.rtt_api.service.note.NoteService;
import com.bunny.rtt_api.service.realtime.engine.EngineSession;
import com.bunny.rtt_api.service.realtime.engine.LiveEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TranscribeWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TranscribeWebSocketHandler.class);

    public static final int MAX_FRAME_SIZE = 8192; // Up to 256 ms of PCM16 at 16 kHz mono (WebAudio buffer friendly)
    public static final long MAX_SESSION_DURATION_MS = 10 * 60 * 1000L; // 10 minutes
    public static final int MAX_CONCURRENT_SESSIONS = 50;

    private final TicketService ticketService;
    private final ObjectProvider<LiveEngine> engineProvider;
    private final NoteService noteService;
    private final ObjectMapper objectMapper;

    private final Map<String, SessionContext> activeSessions = new ConcurrentHashMap<>();

    private static class SessionContext {
        final String sessionId;
        final long createdAt;
        long frameCount = 0;
        LiveEngine engine;
        final java.io.ByteArrayOutputStream audioBuffer = new java.io.ByteArrayOutputStream();

        SessionContext(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public TranscribeWebSocketHandler(
            TicketService ticketService,
            ObjectProvider<LiveEngine> engineProvider,
            VadService vadService,
            NoteService noteService,
            ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.engineProvider = engineProvider;
        this.noteService = noteService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        String ticket = extractQueryParam(uri, "ticket");

        if (ticket == null || ticket.isBlank()) {
            log.warn("WebSocket connection rejected: missing ticket");
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing ticket"));
            return;
        }

        String userId;
        try {
            userId = ticketService.consumeTicket(ticket);
        } catch (Exception e) {
            log.warn("WebSocket connection rejected: invalid or expired ticket ({})", e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or expired ticket"));
            return;
        }

        if (activeSessions.size() >= MAX_CONCURRENT_SESSIONS) {
            log.warn("WebSocket connection rejected: server overloaded (active: {})", activeSessions.size());
            sendEvent(session, WsTranscribeEvent.error("overloaded", "Server transcription capacity reached", true));
            session.close(CloseStatus.SERVICE_OVERLOAD.withReason("Overloaded"));
            return;
        }

        String sessionId = UUID.randomUUID().toString();
        SessionContext context = new SessionContext(sessionId, userId);
        activeSessions.put(session.getId(), context);

        log.info("WebSocket transcribe session established: sessionId={}, userId={}", sessionId, userId);
        sendEvent(session, WsTranscribeEvent.ready(sessionId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SessionContext context = activeSessions.get(session.getId());
        if (context == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String payload = message.getPayload().trim();
        if (payload.equalsIgnoreCase("ping") || payload.contains("\"ping\"")) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            return;
        }

        if (payload.equalsIgnoreCase("flush") || payload.contains("\"flush\"")) {
            if (context.engine != null) {
                context.engine.flush();
            }
            return;
        }

        // Start message JSON
        try {
            WsTranscribeStartMessage startMessage = objectMapper.readValue(payload, WsTranscribeStartMessage.class);

            // Initialize LiveEngine for this session
            LiveEngine engine = engineProvider.getObject();
            context.engine = engine;

            engine.open(new EngineSession() {
                @Override
                public void onPartial(String text, String source, long ts) {
                    log.info("Session {} [partial]: {}", context.sessionId, text);
                    sendEvent(session, WsTranscribeEvent.partial(text, source, ts));
                }

                @Override
                public void onFinal(int seq, String text, long startMs, long endMs, Double confidence, String lang) {
                    log.info("Session {} [final seq={}]: {}", context.sessionId, seq, text);
                    // If noteId is present and mode is append/create, persist note block with
                    // source='dictation' (B18)
                    if (startMessage.noteId() != null && !startMessage.noteId().isBlank()) {
                        try {
                            UUID noteUuid = UUID.fromString(startMessage.noteId());
                            noteService.appendBlock(noteUuid, new BlockAppendRequest(text, "dictation", null));
                        } catch (Exception e) {
                            log.error("Failed to append dictation block to note {}", startMessage.noteId(), e);
                        }
                    }

                    sendEvent(session, WsTranscribeEvent.finalSegment(seq, text, startMs, endMs, confidence, lang));
                }

                @Override
                public void onError(String code, String message, boolean retryable) {
                    sendEvent(session, WsTranscribeEvent.error(code, message, retryable));
                }

                @Override
                public void onClosed(String reason) {
                    sendEvent(session, WsTranscribeEvent.closed(reason));
                }
            });

            log.info("Started transcription engine '{}' for sessionId={}", engine.name(), context.sessionId);
        } catch (Exception e) {
            log.error("Failed to process start message: {}", payload, e);
            sendEvent(session, WsTranscribeEvent.error("bad_frame", "Invalid start message payload", false));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        SessionContext context = activeSessions.get(session.getId());
        if (context == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Enforce max session duration
        long duration = System.currentTimeMillis() - context.createdAt;
        if (duration > MAX_SESSION_DURATION_MS) {
            log.warn("Session {} exceeded max duration (10 min). Closing.", context.sessionId);
            sendEvent(session, WsTranscribeEvent.closed("Session duration limit exceeded (10 minutes)"));
            session.close(CloseStatus.NORMAL.withReason("Session duration limit exceeded"));
            return;
        }

        ByteBuffer buffer = message.getPayload();
        int frameSize = buffer.remaining();

        // Enforce max frame size limit (8192 bytes)
        if (frameSize > MAX_FRAME_SIZE) {
            log.warn("Received oversize audio frame ({} bytes > {} max)", frameSize, MAX_FRAME_SIZE);
            sendEvent(session, WsTranscribeEvent.error("bad_frame", "Audio frame exceeds " + MAX_FRAME_SIZE + " bytes limit", false));
            return;
        }

        context.frameCount++;
        if (context.frameCount == 1 || context.frameCount % 40 == 0) {
            log.info("Session {} received audio frame #{} ({} bytes)", context.sessionId, context.frameCount, frameSize);
        }

        byte[] frameBytes = new byte[frameSize];
        buffer.get(frameBytes);
        try {
            context.audioBuffer.write(frameBytes);
        } catch (Exception ignored) {
        }

        if (context.engine != null) {
            context.engine.sendAudio(ByteBuffer.wrap(frameBytes));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionContext context = activeSessions.remove(session.getId());
        if (context != null) {
            log.info("WebSocket transcribe session closed: sessionId={}, status={}", context.sessionId, status);
            if (context.audioBuffer.size() > 0) {
                saveSessionWav(context.sessionId, context.audioBuffer.toByteArray());
            }
            if (context.engine != null) {
                try {
                    context.engine.flush();
                    context.engine.close();
                } catch (Exception e) {
                    log.error("Error closing engine for session {}", context.sessionId, e);
                }
            }
        }
    }

    private void saveSessionWav(String sessionId, byte[] pcmData) {
        try {
            java.io.File targetDir = new java.io.File("target");
            if (!targetDir.exists()) targetDir.mkdirs();
            java.io.File wavFile = new java.io.File(targetDir, "last_mic_session.wav");
            byte[] header = buildWavHeader(pcmData.length, 16000, 1, 16);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(wavFile)) {
                fos.write(header);
                fos.write(pcmData);
            }
            log.info("Diagnostic audio saved to {} ({} audio bytes, {} ms of 16kHz speech)",
                    wavFile.getAbsolutePath(), pcmData.length, (pcmData.length / 32));
        } catch (Exception e) {
            log.warn("Failed to save diagnostic session WAV: {}", e.getMessage());
        }
    }

    private static byte[] buildWavHeader(int pcmDataLength, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);
        int totalDataLen = pcmDataLength + 36;
        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) blockAlign; header[33] = 0;
        header[34] = (byte) bitsPerSample; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (pcmDataLength & 0xff);
        header[41] = (byte) ((pcmDataLength >> 8) & 0xff);
        header[42] = (byte) ((pcmDataLength >> 16) & 0xff);
        header[43] = (byte) ((pcmDataLength >> 24) & 0xff);
        return header;
    }

    private synchronized void sendEvent(WebSocketSession session, WsTranscribeEvent event) {
        if (!session.isOpen()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            if (!"pong".equals(event.type())) {
                log.info("[BACKEND -> UI] Sending event: type={}, text='{}'", event.type(), event.text() != null ? event.text() : "");
            }
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.debug("Failed to send WebSocket message (session may be closed): {}", e.getMessage());
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
