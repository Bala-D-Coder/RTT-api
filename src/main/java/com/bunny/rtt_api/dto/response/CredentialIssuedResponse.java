package com.bunny.rtt_api.dto.response;

public record CredentialIssuedResponse(
        UserResponse user,
        String password
) {}
