package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record NotePatchRequest(
        String title,

        @Pattern(regexp = "sand|coral|violet|aqua|lime|mint", message = "Invalid note color")
        String color,

        Boolean pinned,

        List<Object> tags,

        List<UUID> tagIds
) {}
