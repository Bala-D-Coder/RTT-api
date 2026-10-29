package com.bunny.rtt_api.service.realtime.engine;

import com.bunny.rtt_api.client.WhisperClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = "app.engine", havingValue = "whisper_local")
public class WhisperEngine implements LiveEngine {

    private static final Logger log = LoggerFactory.getLogger(WhisperEngine.class);

    // 16kHz, 16-bit mono = 32,000 bytes per second
    private static final int MIN_PARTIAL_BYTES = 32000; // 1.0s audio threshold for rapid response
    private static final long MIN_PARTIAL_INTERVAL_MS = 500; // Fast cadence between partial attempts

    private final WhisperClient whisperClient;
    private EngineSession session;
    private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
    private final AtomicInteger sequence = new AtomicInteger(1);
    private final AtomicBoolean isTranscribing = new AtomicBoolean(false);
    private long sessionStartMs = 0;
    private long lastPartialMs = 0;

    public WhisperEngine(WhisperClient whisperClient) {
        this.whisperClient = whisperClient;
    }

    @Override
    public void open(EngineSession s) {
        this.session = s;
        this.sequence.set(1);
        this.isTranscribing.set(false);
        synchronized (audioBuffer) {
            this.audioBuffer.reset();
        }
        this.sessionStartMs = System.currentTimeMillis();
        this.lastPartialMs = System.currentTimeMillis();
        log.info("WhisperEngine session opened");
    }

    @Override
    public void sendAudio(ByteBuffer pcm16) {
        if (session == null || pcm16 == null) {
            return;
        }

        byte[] bytes = new byte[pcm16.remaining()];
        pcm16.get(bytes);

        int currentSize;
        synchronized (audioBuffer) {
            audioBuffer.write(bytes, 0, bytes.length);
            currentSize = audioBuffer.size();
        }

        long now = System.currentTimeMillis();
        // Check if we have accumulated enough audio and respected minimum interval
        if (currentSize >= MIN_PARTIAL_BYTES && (now - lastPartialMs) >= MIN_PARTIAL_INTERVAL_MS) {
            // Guard: ensure only one Whisper partial call is active to prevent CPU thread starvation
            if (isTranscribing.compareAndSet(false, true)) {
                lastPartialMs = now;
                byte[] snapshot;
                synchronized (audioBuffer) {
                    snapshot = audioBuffer.toByteArray();
                }

                // Run transcription asynchronously so the WebSocket audio ingestion loop is never blocked
                CompletableFuture.runAsync(() -> {
                    try {
                        WhisperClient.WhisperResponse resp = whisperClient.transcribe(snapshot);
                        if (resp != null && !resp.text().isBlank() && session != null) {
                            session.onPartial(resp.text(), "whisper", System.currentTimeMillis());
                        }
                    } catch (Exception e) {
                        log.debug("Async partial transcription error: {}", e.getMessage());
                    } finally {
                        isTranscribing.set(false);
                    }
                });
            }
        }
    }

    @Override
    public void flush() {
        if (session == null) {
            return;
        }

        byte[] audioData;
        synchronized (audioBuffer) {
            if (audioBuffer.size() == 0) {
                return;
            }
            audioData = audioBuffer.toByteArray();
            audioBuffer.reset();
        }

        long endMs = System.currentTimeMillis() - sessionStartMs;
        long startMs = Math.max(0, endMs - (audioData.length / 32L)); // 32 bytes per ms at 16kHz 16-bit

        try {
            WhisperClient.WhisperResponse resp = whisperClient.transcribe(audioData);
            if (resp != null && !resp.text().isBlank()) {
                session.onFinal(sequence.getAndIncrement(), resp.text(), startMs, endMs, 0.95, resp.language());
            }
        } catch (Exception e) {
            log.error("Error during WhisperEngine flush: {}", e.getMessage());
        }
    }

    @Override
    public String name() {
        return "whisper_local";
    }

    @Override
    public void close() {
        if (session != null) {
            session.onClosed("normal");
            session = null;
        }
        synchronized (audioBuffer) {
            audioBuffer.reset();
        }
    }
}
