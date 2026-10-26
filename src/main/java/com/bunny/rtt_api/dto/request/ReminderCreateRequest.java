package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReminderCreateRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotNull(message = "dueAt is required")
        Instant dueAt,

        UUID noteId,

        @Pattern(regexp = "none|daily|weekly|weekdays", message = "repeat must be 'none', 'daily', 'weekly', or 'weekdays'")
        String repeat,

        List<Integer> weekdays,

        String timezone
) {}
