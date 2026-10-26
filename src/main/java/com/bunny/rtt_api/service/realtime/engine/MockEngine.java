package com.bunny.rtt_api.service.realtime.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = "app.engine", havingValue = "mock", matchIfMissing = true)
public class MockEngine implements LiveEngine {

    private static final Logger log = LoggerFactory.getLogger(MockEngine.class);

    private EngineSession session;
    private final AtomicInteger sequence = new AtomicInteger(1);
    private int frameCount = 0;
    private long sessionStartMs = 0;

    private static final String[] MOCK_WORDS = {
            "Testing", "realtime", "transcription", "with", "mock", "engine",
            "dictation", "Sprint", "retro", "notes", "action", "items"
    };

    @Override
    public void open(EngineSession s) {
        this.session = s;
        this.frameCount = 0;
        this.sequence.set(1);
        this.sessionStartMs = System.currentTimeMillis();
        log.info("MockEngine opened session");
    }

    @Override
    public void sendAudio(ByteBuffer pcm16) {
        if (session == null || pcm16 == null) {
            return;
        }

        frameCount++;
        int bytes = pcm16.remaining();
        if (bytes == 0) return;

        // Simulate periodic partial transcript every 3 frames (~300ms)
        if (frameCount % 3 == 0) {
            int wordIdx = Math.min((frameCount / 3) - 1, MOCK_WORDS.length - 1);
            StringBuilder partial = new StringBuilder();
            for (int i = 0; i <= wordIdx; i++) {
                if (i > 0) partial.append(" ");
                partial.append(MOCK_WORDS[i % MOCK_WORDS.length]);
            }
            session.onPartial(partial.toString(), "gemini", System.currentTimeMillis());
        }
    }

    @Override
    public void flush() {
        if (session == null) {
            return;
        }

        long endMs = System.currentTimeMillis() - sessionStartMs;
        long startMs = Math.max(0, endMs - (frameCount * 100L));
        
        StringBuilder fullText = new StringBuilder();
        int wordsToInclude = Math.max(1, Math.min(frameCount, MOCK_WORDS.length));
        for (int i = 0; i < wordsToInclude; i++) {
            if (i > 0) fullText.append(" ");
            fullText.append(MOCK_WORDS[i % MOCK_WORDS.length]);
        }

        session.onFinal(sequence.getAndIncrement(), fullText.toString(), startMs, Math.max(endMs, 100), 0.98, "en");
        frameCount = 0;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public void close() {
        if (session != null) {
            session.onClosed("normal");
            session = null;
        }
    }
}
