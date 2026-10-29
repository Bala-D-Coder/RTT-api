package com.bunny.rtt_api.dto.response;

public record ExportPayload(
        String filename,
        String contentType,
        byte[] content
) {}
