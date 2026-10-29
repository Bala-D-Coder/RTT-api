package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReminderPatchRequest(
        String title,

        Instant dueAt,

        UUID noteId,

        @Pattern(regexp = "none|daily|weekly|weekdays", message = "repeat must be 'none', 'daily', 'weekly', or 'weekdays'")
        String repeat,

        List<Integer> weekdays,

        String timezone,

        @Pattern(regexp = "pending|sent|snoozed|done|failed", message = "status must be 'pending', 'sent', 'snoozed', 'done', or 'failed'")
        String status
) {}
