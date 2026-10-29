package com.bunny.rtt_api.service.note;

import com.bunny.rtt_api.dto.request.TagCreateRequest;
import com.bunny.rtt_api.dto.response.TagResponse;

import java.util.List;

public interface TagService {

    List<TagResponse> listTags();

    TagResponse ensureTag(TagCreateRequest request);
}
