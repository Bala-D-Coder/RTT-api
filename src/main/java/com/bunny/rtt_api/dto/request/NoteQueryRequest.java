package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record NoteQueryRequest(
        String q,

        String tag,

        @Pattern(regexp = "^(sand|coral|violet|aqua|lime|mint)?$", message = "Invalid note color")
        String color,

        @Pattern(regexp = "^(active|trashed|all)?$", message = "scope must be 'active', 'trashed', or 'all'")
        String scope,

        Boolean today,

        @Pattern(regexp = "^(all|dictation)?$", message = "source must be 'all' or 'dictation'")
        String source,

        @Pattern(regexp = "^(updated|created|title|purged)?$", message = "sort must be 'updated', 'created', 'title', or 'purged'")
        String sort,

        @Min(value = 1, message = "page must be >= 1")
        Integer page,

        @Min(value = 1, message = "pageSize must be >= 1")
        @Max(value = 100, message = "pageSize must be <= 100")
        Integer pageSize
) {
    public String getEffectiveScope() {
        return scope != null && !scope.isBlank() ? scope.trim() : "active";
    }

    public String getEffectiveSort() {
        return sort != null && !sort.isBlank() ? sort.trim() : "updated";
    }

    public int getEffectivePage() {
        return (page != null && page >= 1) ? page : 1;
    }

    public int getEffectivePageSize() {
        if (pageSize == null || pageSize < 1) return 20;
        return Math.min(pageSize, 100);
    }
}
