package com.bunny.rtt_api.dto.response;

public record SessionResponse(
        UserResponse user,
        String accessToken
) {}
