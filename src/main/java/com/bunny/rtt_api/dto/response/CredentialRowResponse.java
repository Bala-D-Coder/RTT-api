package com.bunny.rtt_api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CredentialRowResponse(
        UUID id,
        String email,
        String label,
        Instant createdAt,
        Instant lastLoginAt,
        Instant expiresAt,
        Instant disabledAt,
        Instant lockedUntil
) {}
