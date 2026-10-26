package com.bunny.rtt_api.dto.realtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WsTranscribeStartMessage(
        Integer sampleRate,
        String encoding,
        Integer channels,
        String noteId,
        String mode
) {}
