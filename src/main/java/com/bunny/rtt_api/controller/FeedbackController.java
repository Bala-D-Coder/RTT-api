package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.dto.request.FeedbackSubmitRequest;
import com.bunny.rtt_api.dto.request.HireInvitationSubmitRequest;
import com.bunny.rtt_api.service.sheets.GoogleSheetsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/feedback")
@Tag(name = "Feedback & Hire", description = "Endpoints for user ratings, reviews, and hire invitations")
public class FeedbackController {

    private final GoogleSheetsService googleSheetsService;

    public FeedbackController(GoogleSheetsService googleSheetsService) {
        this.googleSheetsService = googleSheetsService;
    }

    @PostMapping
    @Operation(summary = "Submit rating and review, syncing to Google Sheets")
    public ResponseEntity<Map<String, Object>> submitFeedback(@Valid @RequestBody FeedbackSubmitRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "feedback");
        payload.put("email", request.email());
        payload.put("rating", request.rating());
        payload.put("review", request.review() != null ? request.review() : "");
        payload.put("role", request.role() != null ? request.role() : "");
        payload.put("timestamp", Instant.now().toString());

        boolean synced = googleSheetsService.dispatch(payload);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "syncedToSheets", synced
        ));
    }

    @PostMapping("/hire")
    @Operation(summary = "Submit interview invitation, syncing to Google Sheets")
    public ResponseEntity<Map<String, Object>> submitHireInvitation(@Valid @RequestBody HireInvitationSubmitRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "hire_invitation");
        payload.put("company", request.company());
        payload.put("contactName", request.contactName());
        payload.put("email", request.email());
        payload.put("interviewType", request.interviewType());
        payload.put("roleTitle", request.roleTitle() != null ? request.roleTitle() : "");
        payload.put("proposedTime", request.proposedTime() != null ? request.proposedTime() : "");
        payload.put("message", request.message() != null ? request.message() : "");
        payload.put("timestamp", Instant.now().toString());

        boolean synced = googleSheetsService.dispatch(payload);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "syncedToSheets", synced
        ));
    }
}
