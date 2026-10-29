package com.bunny.rtt_api.dto.response;

import java.util.UUID;

public record TagResponse(
        UUID id,
        String name
) {}
