package com.bunny.rtt_api.dto.response;

public record ReseedResponse(
        ReseedCounts created
) {
    public record ReseedCounts(
            int notes,
            int blocks,
            int reminders,
            int tags
    ) {}
}
