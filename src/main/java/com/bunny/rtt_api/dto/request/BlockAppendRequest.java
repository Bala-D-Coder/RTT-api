package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record BlockAppendRequest(
        @NotBlank(message = "Block text is required")
        String text,

        @Pattern(regexp = "typed|dictation", message = "source must be 'typed' or 'dictation'")
        String source,

        Instant createdAt
) {}
