package com.bunny.rtt_api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NoteBlockResponse(
        UUID id,
        String text,
        String source,
        Instant createdAt
) {}
