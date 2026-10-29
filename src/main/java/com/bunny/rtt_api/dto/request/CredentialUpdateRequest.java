package com.bunny.rtt_api.dto.request;

public record CredentialUpdateRequest(
        Boolean extend,
        Boolean disabled
) {}
