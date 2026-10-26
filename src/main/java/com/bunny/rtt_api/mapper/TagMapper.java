package com.bunny.rtt_api.mapper;

import com.bunny.rtt_api.dto.response.TagResponse;
import com.bunny.rtt_api.entity.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.Collection;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TagMapper {

    TagResponse toTagResponse(Tag tag);

    List<TagResponse> toTagResponses(Collection<Tag> tags);
}
