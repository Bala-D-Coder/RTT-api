package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.dto.response.RealtimeTicketResponse;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.exception.AppException;
import com.bunny.rtt_api.exception.ErrorCode;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.security.SecurityUtils;
import com.bunny.rtt_api.service.realtime.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/realtime")
@Tag(name = "Realtime", description = "Realtime WebSocket ticket management")
public class RealtimeController {

    private final TicketService ticketService;
    private final UserRepository userRepository;

    public RealtimeController(TicketService ticketService, UserRepository userRepository) {
        this.ticketService = ticketService;
        this.userRepository = userRepository;
    }

    @PostMapping("/ticket")
    @Operation(summary = "Generate a single-use ticket for WebSocket connections")
    public ResponseEntity<RealtimeTicketResponse> createTicket() {
        User user = SecurityUtils.getCurrentUserId()
                .flatMap(idStr -> {
                    try {
                        return userRepository.findById(UUID.fromString(idStr));
                    } catch (Exception e) {
                        return userRepository.findByEmail(idStr);
                    }
                })
                .or(() -> SecurityUtils.getCurrentUserEmail().flatMap(userRepository::findByEmail))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED,
                        "User account not found"));

        RealtimeTicketResponse response = ticketService.createTicket(user);
        return ResponseEntity.ok(response);
    }
}
