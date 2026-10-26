package com.bunny.rtt_api.service.reminder.impl;

import com.bunny.rtt_api.dto.request.ReminderCreateRequest;
import com.bunny.rtt_api.dto.request.ReminderPatchRequest;
import com.bunny.rtt_api.dto.request.ReminderQueryRequest;
import com.bunny.rtt_api.dto.request.ReminderSnoozeRequest;
import com.bunny.rtt_api.dto.response.PageResponse;
import com.bunny.rtt_api.dto.response.ReminderResponse;
import com.bunny.rtt_api.entity.Note;
import com.bunny.rtt_api.entity.Reminder;
import com.bunny.rtt_api.exception.AppException;
import com.bunny.rtt_api.exception.ErrorCode;
import com.bunny.rtt_api.mapper.ReminderMapper;
import com.bunny.rtt_api.repository.NoteRepository;
import com.bunny.rtt_api.repository.ReminderRepository;
import com.bunny.rtt_api.security.SecurityUtils;
import com.bunny.rtt_api.service.reminder.ReminderService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final ReminderRepository reminderRepository;
    private final NoteRepository noteRepository;
    private final ReminderMapper reminderMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReminderResponse> listReminders(ReminderQueryRequest query) {
        String scope = query.getEffectiveScope();
        int page = query.getEffectivePage();
        int pageSize = query.getEffectivePageSize();
        UUID noteId = query.noteId();

        Specification<Reminder> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if ("upcoming".equalsIgnoreCase(scope)) {
                predicates.add(root.get("status").in("pending", "snoozed"));
                Join<Reminder, Note> noteJoin = root.join("note", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.isNull(root.get("note")),
                        cb.isNull(noteJoin.get("deletedAt"))
                ));
            }

            if (noteId != null) {
                predicates.add(cb.equal(root.get("note").get("id"), noteId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = Sort.by(Sort.Direction.ASC, "dueAt");
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);

        Page<Reminder> reminderPage = reminderRepository.findAll(spec, pageable);
        List<ReminderResponse> items = reminderMapper.toReminderResponses(reminderPage.getContent());

        return new PageResponse<>(items, page, pageSize, reminderPage.getTotalElements());
    }

    @Override
    @Transactional
    public ReminderResponse createReminder(ReminderCreateRequest request) {
        String currentUserEmail = SecurityUtils.getCurrentUserEmail().orElse("system");

        Note note = null;
        if (request.noteId() != null) {
            note = noteRepository.findById(request.noteId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOTE_NOT_FOUND, HttpStatus.NOT_FOUND, "Note not found"));
        }

        Integer[] weekdays = request.weekdays() != null ? request.weekdays().toArray(new Integer[0]) : null;

        Reminder reminder = Reminder.builder()
                .createdBy(currentUserEmail)
                .note(note)
                .title(request.title())
                .dueAt(request.dueAt())
                .timezone(request.timezone() != null && !request.timezone().isBlank() ? request.timezone() : "Asia/Kolkata")
                .repeat(request.repeat() != null && !request.repeat().isBlank() ? request.repeat() : "none")
                .weekdays(weekdays)
                .status("pending")
                .build();

        Reminder saved = reminderRepository.save(reminder);
        return reminderMapper.toReminderResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReminderResponse getReminder(UUID id) {
        Reminder reminder = findReminderById(id);
        return reminderMapper.toReminderResponse(reminder);
    }

    @Override
    @Transactional
    public ReminderResponse patchReminder(UUID id, ReminderPatchRequest request) {
        Reminder reminder = findReminderById(id);

        if (request.title() != null && !request.title().isBlank()) {
            reminder.setTitle(request.title());
        }
        if (request.dueAt() != null) {
            reminder.setDueAt(request.dueAt());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            reminder.setTimezone(request.timezone());
        }
        if (request.repeat() != null) {
            reminder.setRepeat(request.repeat());
        }
        if (request.weekdays() != null) {
            reminder.setWeekdays(request.weekdays().toArray(new Integer[0]));
        }
        if (request.status() != null && !request.status().isBlank()) {
            reminder.setStatus(request.status());
        }
        if (request.noteId() != null) {
            Note note = noteRepository.findById(request.noteId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOTE_NOT_FOUND, HttpStatus.NOT_FOUND, "Note not found"));
            reminder.setNote(note);
        }

        reminder.setUpdatedAt(Instant.now());
        Reminder saved = reminderRepository.save(reminder);
        return reminderMapper.toReminderResponse(saved);
    }

    @Override
    @Transactional
    public void deleteReminder(UUID id) {
        Reminder reminder = findReminderById(id);
        reminderRepository.delete(reminder);
    }

    @Override
    @Transactional
    public ReminderResponse snoozeReminder(UUID id, ReminderSnoozeRequest request) {
        Reminder reminder = findReminderById(id);

        if ("done".equalsIgnoreCase(reminder.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Cannot snooze completed reminder");
        }

        reminder.setDueAt(request.to());
        reminder.setStatus("snoozed");
        reminder.setUpdatedAt(Instant.now());

        Reminder saved = reminderRepository.save(reminder);
        return reminderMapper.toReminderResponse(saved);
    }

    @Override
    @Transactional
    public ReminderResponse completeReminder(UUID id) {
        Reminder reminder = findReminderById(id);
        reminder.setStatus("done");
        reminder.setUpdatedAt(Instant.now());

        Reminder saved = reminderRepository.save(reminder);
        return reminderMapper.toReminderResponse(saved);
    }

    private Reminder findReminderById(UUID id) {
        return reminderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.REMINDER_NOT_FOUND, HttpStatus.NOT_FOUND, "Reminder not found"));
    }
}
