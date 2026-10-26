package com.bunny.rtt_api.dto.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsTranscribeEvent(
        String type,
        String sessionId,
        String text,
        String source,
        Long ts,
        Integer seq,
        Long startMs,
        Long endMs,
        Double confidence,
        String lang,
        String code,
        String message,
        Boolean retryable,
        String reason
) {
    public static WsTranscribeEvent ready(String sessionId) {
        return new WsTranscribeEvent("ready", sessionId, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static WsTranscribeEvent partial(String text, String source, long ts) {
        return new WsTranscribeEvent("partial", null, text, source, ts, null, null, null, null, null, null, null, null, null);
    }

    public static WsTranscribeEvent finalSegment(int seq, String text, long startMs, long endMs, Double confidence, String lang) {
        return new WsTranscribeEvent("final", null, text, null, null, seq, startMs, endMs, confidence, lang, null, null, null, null);
    }

    public static WsTranscribeEvent error(String code, String message, boolean retryable) {
        return new WsTranscribeEvent("error", null, null, null, null, null, null, null, null, null, code, message, retryable, null);
    }

    public static WsTranscribeEvent closed(String reason) {
        return new WsTranscribeEvent("closed", null, null, null, null, null, null, null, null, null, null, null, null, reason);
    }
}
