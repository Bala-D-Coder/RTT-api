package com.bunny.rtt_api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public record NoteCreateRequest(
        String title,

        @Pattern(regexp = "sand|coral|violet|aqua|lime|mint", message = "Invalid note color")
        String color,

        @Valid
        List<BlockInput> body
) {
    public record BlockInput(
            String text,

            @Pattern(regexp = "typed|dictation", message = "source must be 'typed' or 'dictation'")
            String source,

            Instant createdAt
    ) {}
}
