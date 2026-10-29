package com.bunny.rtt_api.controller;

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
import com.bunny.rtt_api.service.note.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
@Tag(name = "Notes", description = "Notes, blocks, tags, bin, and export endpoints")
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    @Operation(summary = "List and search notes with dynamic filters, pagination, and sorting")
    public ResponseEntity<PageResponse<NoteResponse>> listNotes(@Valid NoteQueryRequest query) {
        return ResponseEntity.ok(noteService.listNotes(query));
    }

    @PostMapping
    @Operation(summary = "Create a new note with optional initial blocks in a single transaction")
    public ResponseEntity<NoteResponse> createNote(@Valid @RequestBody NoteCreateRequest request) {
        NoteResponse created = noteService.createNote(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single note by ID")
    public ResponseEntity<NoteResponse> getNote(@PathVariable UUID id) {
        return ResponseEntity.ok(noteService.getNote(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update scalar note fields (title, color, pinned) and optionally tags")
    public ResponseEntity<NoteResponse> patchNote(
            @PathVariable UUID id,
            @Valid @RequestBody NotePatchRequest request
    ) {
        return ResponseEntity.ok(noteService.patchNote(id, request));
    }

    @PostMapping("/{id}/blocks")
    @Operation(summary = "Append a new block to the note at position max + 1")
    public ResponseEntity<NoteResponse> appendBlock(
            @PathVariable UUID id,
            @Valid @RequestBody BlockAppendRequest request
    ) {
        return ResponseEntity.ok(noteService.appendBlock(id, request));
    }

    @PutMapping("/{id}/blocks/{blockId}")
    @Operation(summary = "Update block text on an existing note block")
    public ResponseEntity<NoteResponse> updateBlock(
            @PathVariable UUID id,
            @PathVariable UUID blockId,
            @Valid @RequestBody BlockUpdateRequest request
    ) {
        return ResponseEntity.ok(noteService.updateBlock(id, blockId, request));
    }

    @DeleteMapping("/{id}/blocks/{blockId}")
    @Operation(summary = "Delete a block from the note")
    public ResponseEntity<NoteResponse> deleteBlock(
            @PathVariable UUID id,
            @PathVariable UUID blockId
    ) {
        return ResponseEntity.ok(noteService.deleteBlock(id, blockId));
    }

    @PostMapping("/{id}/pin")
    @Operation(summary = "Toggle pinned state of the note")
    public ResponseEntity<NoteResponse> togglePin(@PathVariable UUID id) {
        return ResponseEntity.ok(noteService.togglePin(id));
    }

    @PutMapping("/{id}/tags")
    @Operation(summary = "Replace note tags idempotently")
    public ResponseEntity<NoteResponse> updateTags(
            @PathVariable UUID id,
            @Valid @RequestBody NoteTagsUpdateRequest request
    ) {
        return ResponseEntity.ok(noteService.updateTags(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a note into the bin with 7-day retention")
    public ResponseEntity<NoteResponse> trashNote(@PathVariable UUID id) {
        return ResponseEntity.ok(noteService.trashNote(id));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore a trashed note from the bin and re-arm reminders")
    public ResponseEntity<NoteResponse> restoreNote(@PathVariable UUID id) {
        return ResponseEntity.ok(noteService.restoreNote(id));
    }

    @DeleteMapping("/{id}/purge")
    @Operation(summary = "Hard delete a note permanently from the bin (409 if live)")
    public ResponseEntity<Void> purgeNote(@PathVariable UUID id) {
        noteService.purgeNote(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Empty all trashed notes from the bin")
    public ResponseEntity<PurgedCountResponse> emptyBin(
            @RequestParam(name = "scope", defaultValue = "trashed") String scope
    ) {
        return ResponseEntity.ok(noteService.emptyBin());
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Export note as Markdown, plain text, or JSON with Content-Disposition")
    public ResponseEntity<byte[]> exportNote(
            @PathVariable UUID id,
            @RequestParam(name = "format", defaultValue = "md") String format
    ) {
        ExportPayload payload = noteService.exportNote(id, format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(payload.contentType()));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(payload.filename(), StandardCharsets.UTF_8)
                        .build()
        );
        return new ResponseEntity<>(payload.content(), headers, HttpStatus.OK);
    }
}
