package com.bunny.rtt_api.dto.request;

import java.util.List;
import java.util.UUID;

public record NoteTagsUpdateRequest(
        List<UUID> tagIds,
        List<Object> tags
) {}
