package com.bunny.rtt_api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoteResponse(
        UUID id,
        String title,
        List<NoteBlockResponse> body,
        String color,
        boolean pinned,
        List<TagResponse> tags,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Instant purgeAt
) {}
