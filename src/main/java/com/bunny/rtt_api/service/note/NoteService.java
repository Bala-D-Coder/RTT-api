package com.bunny.rtt_api.service.note;

import com.bunny.rtt_api.dto.request.BlockAppendRequest;
import com.bunny.rtt_api.dto.request.BlockUpdateRequest;
import com.bunny.rtt_api.dto.request.NoteCreateRequest;
import com.bunny.rtt_api.dto.request.NotePatchRequest;
import com.bunny.rtt_api.dto.request.NoteQueryRequest;
import com.bunny.rtt_api.dto.request.NoteTagsUpdateRequest;
import com.bunny.rtt_api.dto.response.ExportPayload;
import com.bunny.rtt_api.dto.response.NoteResponse;
import com.bunny.rtt_api.dto.response.PageResponse;
import com.bunny.rtt_api.dto.response.PurgedCountResponse;

import java.util.UUID;

public interface NoteService {

    PageResponse<NoteResponse> listNotes(NoteQueryRequest query);

    NoteResponse createNote(NoteCreateRequest request);

    NoteResponse getNote(UUID id);

    NoteResponse patchNote(UUID id, NotePatchRequest request);

    NoteResponse appendBlock(UUID id, BlockAppendRequest request);

    NoteResponse updateBlock(UUID noteId, UUID blockId, BlockUpdateRequest request);

    NoteResponse deleteBlock(UUID noteId, UUID blockId);

    NoteResponse togglePin(UUID id);

    NoteResponse updateTags(UUID id, NoteTagsUpdateRequest request);

    NoteResponse trashNote(UUID id);

    NoteResponse restoreNote(UUID id);

    void purgeNote(UUID id);

    PurgedCountResponse emptyBin();

    ExportPayload exportNote(UUID id, String format);
}
