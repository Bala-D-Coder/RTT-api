package com.bunny.rtt_api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        UUID noteId,
        String title,
        Instant dueAt,
        String timezone,
        String repeat,
        List<Integer> weekdays,
        String status,
        Instant createdAt
) {}
