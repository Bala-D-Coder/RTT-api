package com.bunny.rtt_api.service.realtime.engine;

import com.bunny.rtt_api.client.GeminiLiveClient;
import com.bunny.rtt_api.config.AppProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = "app.engine", havingValue = "gemini_live")
public class GeminiLiveEngine implements LiveEngine {

    private final AppProps appProps;
    private final ObjectMapper objectMapper;
    private GeminiLiveClient client;
    private EngineSession session;
    private final AtomicInteger sequence = new AtomicInteger(1);
    private final StringBuilder segmentBuffer = new StringBuilder();
    private volatile String lastInterimText = null;
    private long segmentStartMs = 0;

    public GeminiLiveEngine(AppProps appProps, ObjectMapper objectMapper) {
        this.appProps = appProps;
        this.objectMapper = objectMapper;
    }

    @Override
    public void open(EngineSession s) {
        this.session = s;
        this.sequence.set(1);
        this.segmentBuffer.setLength(0);
        this.lastInterimText = null;
        this.segmentStartMs = System.currentTimeMillis();

        String apiKey = appProps.gemini() != null ? appProps.gemini().apiKey() : null;
        String model = appProps.gemini() != null ? appProps.gemini().model() : null;
        this.client = new GeminiLiveClient(apiKey, model, objectMapper);

        client.connect(new GeminiLiveClient.Listener() {
            @Override
            public void onInterim(String text) {
                lastInterimText = text;
                if (session != null) {
                    session.onPartial(text, "gemini", System.currentTimeMillis());
                }
            }

            @Override
            public void onFinalSegment(String text) {
                lastInterimText = null;
                if (session != null && !text.isBlank()) {
                    long now = System.currentTimeMillis();
                    session.onFinal(sequence.getAndIncrement(), text, segmentStartMs, now, 0.98, "en");
                    segmentStartMs = now;
                }
            }

            @Override
            public void onTextDelta(String text) {
                segmentBuffer.append(text);
                session.onPartial(segmentBuffer.toString(), "gemini", System.currentTimeMillis());
            }

            @Override
            public void onTurnComplete() {
                flush();
            }

            @Override
            public void onError(Throwable error) {
                session.onError("gemini_error", error.getMessage(), true);
            }

            @Override
            public void onClose(int statusCode, String reason) {
                session.onClosed(reason);
            }
        });
    }

    @Override
    public void sendAudio(ByteBuffer pcm16) {
        if (client != null) {
            client.sendAudioChunk(pcm16);
        }
    }

    @Override
    public void flush() {
        if (client != null) {
            client.flush();
        }
        if (lastInterimText != null && !lastInterimText.isBlank() && session != null) {
            long now = System.currentTimeMillis();
            long startMs = segmentStartMs;
            String text = lastInterimText.trim();
            lastInterimText = null;
            segmentStartMs = now;
            session.onFinal(sequence.getAndIncrement(), text, startMs, now, 0.98, "en");
        }
        if (segmentBuffer.length() > 0 && session != null) {
            long now = System.currentTimeMillis();
            long startMs = segmentStartMs;
            long endMs = now;
            String text = segmentBuffer.toString().trim();
            segmentBuffer.setLength(0);
            segmentStartMs = now;

            if (!text.isEmpty()) {
                session.onFinal(sequence.getAndIncrement(), text, startMs, endMs, 0.95, "en");
            }
        }
    }

    @Override
    public String name() {
        return "gemini_live";
    }

    @Override
    public void close() {
        flush();
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
