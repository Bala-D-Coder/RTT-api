package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.dto.request.ReminderCreateRequest;
import com.bunny.rtt_api.dto.request.ReminderPatchRequest;
import com.bunny.rtt_api.dto.request.ReminderQueryRequest;
import com.bunny.rtt_api.dto.request.ReminderSnoozeRequest;
import com.bunny.rtt_api.dto.response.PageResponse;
import com.bunny.rtt_api.dto.response.ReminderResponse;
import com.bunny.rtt_api.service.reminder.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reminders")
@RequiredArgsConstructor
@Tag(name = "Reminders", description = "Reminder CRUD, snooze, and completion endpoints")
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    @Operation(summary = "List reminders with scope and noteId filters")
    public ResponseEntity<PageResponse<ReminderResponse>> listReminders(@Valid ReminderQueryRequest query) {
        return ResponseEntity.ok(reminderService.listReminders(query));
    }

    @PostMapping
    @Operation(summary = "Create a new reminder")
    public ResponseEntity<ReminderResponse> createReminder(@Valid @RequestBody ReminderCreateRequest request) {
        ReminderResponse created = reminderService.createReminder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reminder by ID")
    public ResponseEntity<ReminderResponse> getReminder(@PathVariable UUID id) {
        return ResponseEntity.ok(reminderService.getReminder(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update reminder fields")
    public ResponseEntity<ReminderResponse> patchReminder(
            @PathVariable UUID id,
            @Valid @RequestBody ReminderPatchRequest request
    ) {
        return ResponseEntity.ok(reminderService.patchReminder(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a reminder")
    public ResponseEntity<Void> deleteReminder(@PathVariable UUID id) {
        reminderService.deleteReminder(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/snooze")
    @Operation(summary = "Snooze a reminder to a specific future timestamp")
    public ResponseEntity<ReminderResponse> snoozeReminder(
            @PathVariable UUID id,
            @Valid @RequestBody ReminderSnoozeRequest request
    ) {
        return ResponseEntity.ok(reminderService.snoozeReminder(id, request));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark a reminder series or item as completed (status = done)")
    public ResponseEntity<ReminderResponse> completeReminder(@PathVariable UUID id) {
        return ResponseEntity.ok(reminderService.completeReminder(id));
    }
}
