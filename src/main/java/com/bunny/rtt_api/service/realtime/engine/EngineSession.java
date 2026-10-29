package com.bunny.rtt_api.service.realtime.engine;

public interface EngineSession {
    void onPartial(String text, String source, long ts);
    void onFinal(int seq, String text, long startMs, long endMs, Double confidence, String lang);
    void onError(String code, String message, boolean retryable);
    void onClosed(String reason);
}
