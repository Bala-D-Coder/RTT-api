package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ReminderSnoozeRequest(
        @NotNull(message = "'to' timestamp is required")
        Instant to
) {}
