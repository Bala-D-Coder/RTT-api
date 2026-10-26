package com.bunny.rtt_api.dto.response;

public record RealtimeTicketResponse(
        String ticket,
        int expiresIn
) {}
