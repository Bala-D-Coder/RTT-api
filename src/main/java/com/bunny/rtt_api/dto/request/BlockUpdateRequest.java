package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.NotNull;

public record BlockUpdateRequest(
        @NotNull(message = "Block text is required")
        String text
) {}
