package com.bunny.rtt_api.service.note.impl;

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
import com.bunny.rtt_api.entity.Note;
import com.bunny.rtt_api.entity.NoteBlock;
import com.bunny.rtt_api.entity.Tag;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.exception.AppException;
import com.bunny.rtt_api.exception.ErrorCode;
import com.bunny.rtt_api.mapper.NoteMapper;
import com.bunny.rtt_api.repository.NoteRepository;
import com.bunny.rtt_api.repository.TagRepository;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.repository.specification.NoteSpecifications;
import com.bunny.rtt_api.security.SecurityUtils;
import com.bunny.rtt_api.service.note.NoteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bunny.rtt_api.service.notification.NotificationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final NoteMapper noteMapper;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NoteResponse> listNotes(NoteQueryRequest query) {
        String scope = query.getEffectiveScope();
        String sort = query.getEffectiveSort();
        int page = query.getEffectivePage();
        int pageSize = query.getEffectivePageSize();

        // 1. Timezone boundary resolution for today=true
        Instant startOfDay = null;
        Instant endOfDay = null;
        if (Boolean.TRUE.equals(query.today())) {
            User currentUser = getCurrentUserEntity().orElse(null);
            ZoneId zone;
            try {
                zone = (currentUser != null && currentUser.getTimezone() != null && !currentUser.getTimezone().isBlank())
                        ? ZoneId.of(currentUser.getTimezone())
                        : ZoneId.of("Asia/Kolkata");
            } catch (Exception e) {
                zone = ZoneId.of("Asia/Kolkata");
            }
            ZonedDateTime nowInZone = ZonedDateTime.now(zone);
            startOfDay = nowInZone.toLocalDate().atStartOfDay(zone).toInstant();
            endOfDay = nowInZone.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        }

        // 2. Sorting mapping: updated | created | title | purged
        Sort sortObj;
        switch (sort.toLowerCase()) {
            case "purged" -> sortObj = Sort.by(Sort.Direction.ASC, "purgeAt");
            case "title" -> sortObj = Sort.by(Sort.Direction.ASC, "title");
            case "created" -> sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
            default -> {
                if ("trashed".equalsIgnoreCase(scope)) {
                    sortObj = Sort.by(Sort.Direction.DESC, "updatedAt");
                } else {
                    sortObj = Sort.by(Sort.Direction.DESC, "pinned")
                            .and(Sort.by(Sort.Direction.DESC, "updatedAt"));
                }
            }
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, sortObj);
        Specification<Note> spec = NoteSpecifications.buildFilter(
                query.q(),
                query.tag(),
                query.color(),
                scope,
                query.today(),
                startOfDay,
                endOfDay,
                query.source()
        );

        Page<Note> notePage = noteRepository.findAll(spec, pageable);
        List<NoteResponse> items = noteMapper.toNoteResponses(notePage.getContent());

        return new PageResponse<>(items, page, pageSize, notePage.getTotalElements());
    }

    @Override
    @Transactional
    public NoteResponse createNote(NoteCreateRequest request) {
        String currentUserEmail = SecurityUtils.getCurrentUserEmail().orElse("system");

        Note note = Note.builder()
                .createdBy(currentUserEmail)
                .title(request.title() != null ? request.title() : "")
                .color(request.color() != null ? request.color() : "sand")
                .pinned(false)
                .build();

        if (request.body() != null && !request.body().isEmpty()) {
            for (int i = 0; i < request.body().size(); i++) {
                NoteCreateRequest.BlockInput bi = request.body().get(i);
                NoteBlock block = NoteBlock.builder()
                        .text(bi.text() != null ? bi.text() : "")
                        .source(bi.source() != null ? bi.source() : "typed")
                        .position(i)
                        .createdAt(bi.createdAt() != null ? bi.createdAt() : Instant.now())
                        .build();
                note.addBlock(block);
            }
        }

        Note saved = noteRepository.save(note);
        notificationService.notifyNoteUpdated(saved.getId());
        return noteMapper.toNoteResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NoteResponse getNote(UUID id) {
        Note note = findNoteById(id);
        return noteMapper.toNoteResponse(note);
    }

    @Override
    @Transactional
    public NoteResponse patchNote(UUID id, NotePatchRequest request) {
        Note note = findNoteById(id);

        if (request.title() != null) {
            note.setTitle(request.title());
        }
        if (request.color() != null) {
            note.setColor(request.color());
        }
        if (request.pinned() != null) {
            note.setPinned(request.pinned());
        }

        if (request.tagIds() != null || request.tags() != null) {
            Set<UUID> tagIds = extractTagIds(request.tagIds(), request.tags());
            if (tagIds.isEmpty()) {
                note.getTags().clear();
            } else {
                List<Tag> tags = tagRepository.findAllById(tagIds);
                note.setTags(new HashSet<>(tags));
            }
        }

        note.setUpdatedAt(Instant.now());
        Note saved = noteRepository.save(note);
        notificationService.notifyNoteUpdated(saved.getId());
        return noteMapper.toNoteResponse(saved);
    }

    @Override
    @Transactional
    public NoteResponse appendBlock(UUID id, BlockAppendRequest request) {
        Note note = findNoteById(id);

        int maxPos = note.getBlocks().stream()
                .mapToInt(NoteBlock::getPosition)
                .max()
                .orElse(-1);
        int nextPos = maxPos + 1;

        NoteBlock block = NoteBlock.builder()
                .text(request.text() != null ? request.text() : "")
                .source(request.source() != null ? request.source() : "typed")
                .position(nextPos)
                .createdAt(request.createdAt() != null ? request.createdAt() : Instant.now())
                .build();

        note.addBlock(block);
        note.setUpdatedAt(Instant.now());
        Note saved = noteRepository.save(note);
        notificationService.notifyNoteUpdated(saved.getId());
        return noteMapper.toNoteResponse(saved);
    }

    @Override
    @Transactional
    public NoteResponse updateBlock(UUID noteId, UUID blockId, BlockUpdateRequest request) {
        Note note = findNoteById(noteId);

        NoteBlock block = note.getBlocks().stream()
                .filter(b -> b.getId().equals(blockId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.BLOCK_NOT_FOUND, HttpStatus.NOT_FOUND, "Block not found"));

        block.setText(request.text() != null ? request.text() : "");
        note.setUpdatedAt(Instant.now());
        Note saved = noteRepository.save(note);
        notificationService.notifyNoteUpdated(saved.getId());
        return noteMapper.toNoteResponse(saved);
    }

    @Override
    @Transactional
    public NoteResponse deleteBlock(UUID noteId, UUID blockId) {
        Note note = findNoteById(noteId);

        NoteBlock block = note.getBlocks().stream()
                .filter(b -> b.getId().equals(blockId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.BLOCK_NOT_FOUND, HttpStatus.NOT_FOUND, "Block not found"));

        note.removeBlock(block);
        note.setUpdatedAt(Instant.now());
        Note saved = noteRepository.save(note);
        notificationService.notifyNoteUpdated(saved.getId());
        return noteMapper.toNoteResponse(saved);
    }

    @Override
    @Transactional
    public NoteResponse togglePin(UUID id) {
        Note note = findNoteById(id);
        note.setPinned(!note.isPinned());
        note.setUpdatedAt(Instant.now());
        Note saved = noteRepository.save(note);
        notificationService.notifyNoteUpdated(saved.getId());
        return noteMapper.toNoteResponse(saved);
    }

    @Override
    @Transactional
    public NoteResponse updateTags(UUID id, NoteTagsUpdateRequest request) {
        Note note = findNoteById(id);

        Set<UUID> tagIds = extractTagIds(request.tagIds(), request.tags());
        if (tagIds.isEmpty()) {
            note.getTags().clear();
        } else {
            List<Tag> tags = tagRepository.findAllById(tagIds);
            note.setTags(new HashSet<>(tags));
        }

        note.setUpdatedAt(Instant.now());
        Note saved = noteRepository.save(note);
        notificationService.notifyNoteUpdated(saved.getId());
        return noteMapper.toNoteResponse(saved);
    }

    @Override
    @Transactional
    public NoteResponse trashNote(UUID id) {
        Note note = findNoteById(id);

        // Idempotent: never reset existing deletedAt / purgeAt
        if (note.getDeletedAt() == null) {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            note.setDeletedAt(now);
            note.setPurgeAt(now.plus(7, ChronoUnit.DAYS));
            note.setUpdatedAt(now);
            note = noteRepository.save(note);
        }

        notificationService.notifyNoteUpdated(note.getId());
        return noteMapper.toNoteResponse(note);
    }

    @Override
    @Transactional
    public NoteResponse restoreNote(UUID id) {
        Note note = findNoteById(id);

        note.setDeletedAt(null);
        note.setPurgeAt(null);
        note.setUpdatedAt(Instant.now());
        Note saved = noteRepository.save(note);
        notificationService.notifyNoteUpdated(saved.getId());
        return noteMapper.toNoteResponse(saved);
    }

    @Override
    @Transactional
    public void purgeNote(UUID id) {
        Note note = findNoteById(id);

        if (note.getDeletedAt() == null) {
            throw new AppException(ErrorCode.NOTE_NOT_IN_BIN, HttpStatus.CONFLICT, "Note is not in bin");
        }

        noteRepository.delete(note);
        notificationService.notifyNoteUpdated(id);
    }

    @Override
    @Transactional
    public PurgedCountResponse emptyBin() {
        List<Note> trashed = noteRepository.findByDeletedAtIsNotNull();
        int count = trashed.size();
        if (count > 0) {
            noteRepository.deleteAll(trashed);
        }
        return new PurgedCountResponse(count);
    }

    @Override
    @Transactional(readOnly = true)
    public ExportPayload exportNote(UUID id, String format) {
        Note note = findNoteById(id);

        String fmt = format != null ? format.trim().toLowerCase() : "md";
        if (!List.of("md", "txt", "json").contains(fmt)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Invalid export format. Must be md, txt, or json");
        }

        String safeTitle = note.getTitle() != null
                ? note.getTitle().replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim()
                : "";
        if (safeTitle.isBlank()) {
            safeTitle = "note";
        }
        String filename = safeTitle + "." + fmt;

        switch (fmt) {
            case "json" -> {
                NoteResponse resp = noteMapper.toNoteResponse(note);
                try {
                    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resp);
                    return new ExportPayload(filename, "application/json", json.getBytes(StandardCharsets.UTF_8));
                } catch (JsonProcessingException e) {
                    throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize note export");
                }
            }
            case "md" -> {
                StringBuilder sb = new StringBuilder();
                if (note.getTitle() != null && !note.getTitle().isBlank()) {
                    sb.append("# ").append(note.getTitle()).append("\n\n");
                }
                for (NoteBlock b : note.getBlocks()) {
                    sb.append(b.getText()).append("\n\n");
                }
                return new ExportPayload(filename, "text/markdown; charset=UTF-8", sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            case "txt" -> {
                StringBuilder sb = new StringBuilder();
                if (note.getTitle() != null && !note.getTitle().isBlank()) {
                    sb.append(note.getTitle()).append("\n\n");
                }
                for (NoteBlock b : note.getBlocks()) {
                    sb.append(b.getText()).append("\n\n");
                }
                return new ExportPayload(filename, "text/plain; charset=UTF-8", sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            default -> throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Unsupported format");
        }
    }

    private Note findNoteById(UUID id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOTE_NOT_FOUND, HttpStatus.NOT_FOUND, "Note not found"));
    }

    private Optional<User> getCurrentUserEntity() {
        return SecurityUtils.getCurrentUserId()
                .flatMap(idStr -> {
                    try {
                        return userRepository.findById(UUID.fromString(idStr));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                })
                .or(() -> SecurityUtils.getCurrentUserEmail().flatMap(userRepository::findByEmail));
    }

    private Set<UUID> extractTagIds(List<UUID> tagIds, List<Object> tags) {
        Set<UUID> result = new HashSet<>();
        if (tagIds != null) {
            for (UUID id : tagIds) {
                if (id != null) result.add(id);
            }
        }
        if (tags != null) {
            for (Object item : tags) {
                if (item instanceof UUID u) {
                    result.add(u);
                } else if (item instanceof String s && !s.isBlank()) {
                    try {
                        result.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {}
                } else if (item instanceof Map<?, ?> m) {
                    Object idObj = m.get("id");
                    if (idObj instanceof String s && !s.isBlank()) {
                        try {
                            result.add(UUID.fromString(s));
                        } catch (IllegalArgumentException ignored) {}
                    } else if (idObj instanceof UUID u) {
                        result.add(u);
                    }
                }
            }
        }
        return result;
    }
}
