package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TagCreateRequest(
        @NotBlank(message = "Tag name is required")
        String name
) {}
