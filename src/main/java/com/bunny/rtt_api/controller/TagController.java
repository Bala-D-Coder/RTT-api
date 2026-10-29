package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.dto.request.TagCreateRequest;
import com.bunny.rtt_api.dto.response.TagResponse;
import com.bunny.rtt_api.service.note.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags", description = "Tag management and ensure-by-name operations")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "List all tags ordered alphabetically")
    public ResponseEntity<List<TagResponse>> listTags() {
        return ResponseEntity.ok(tagService.listTags());
    }

    @PostMapping
    @Operation(summary = "Ensure tag exists by name (case-insensitive lookup or create)")
    public ResponseEntity<TagResponse> ensureTag(@Valid @RequestBody TagCreateRequest request) {
        return ResponseEntity.ok(tagService.ensureTag(request));
    }
}
