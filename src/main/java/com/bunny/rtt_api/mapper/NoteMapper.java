package com.bunny.rtt_api.mapper;

import com.bunny.rtt_api.dto.response.NoteBlockResponse;
import com.bunny.rtt_api.dto.response.NoteResponse;
import com.bunny.rtt_api.entity.Note;
import com.bunny.rtt_api.entity.NoteBlock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {TagMapper.class})
public interface NoteMapper {

    @Mapping(source = "blocks", target = "body")
    NoteResponse toNoteResponse(Note note);

    List<NoteResponse> toNoteResponses(List<Note> notes);

    NoteBlockResponse toNoteBlockResponse(NoteBlock block);

    List<NoteBlockResponse> toNoteBlockResponses(List<NoteBlock> blocks);
}
