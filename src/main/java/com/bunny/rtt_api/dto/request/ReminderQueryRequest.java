package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ReminderQueryRequest(
        @Pattern(regexp = "^(upcoming|all)?$", message = "scope must be 'upcoming' or 'all'")
        String scope,

        UUID noteId,

        @Min(value = 1, message = "page must be >= 1")
        Integer page,

        @Min(value = 1, message = "pageSize must be >= 1")
        @Max(value = 100, message = "pageSize must be <= 100")
        Integer pageSize
) {
    public String getEffectiveScope() {
        return scope != null && !scope.isBlank() ? scope.trim() : "upcoming";
    }

    public int getEffectivePage() {
        return (page != null && page >= 1) ? page : 1;
    }

    public int getEffectivePageSize() {
        if (pageSize == null || pageSize < 1) return 20;
        return Math.min(pageSize, 100);
    }
}
