package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.dto.request.LoginRequest;
import com.bunny.rtt_api.dto.response.SessionResponse;
import com.bunny.rtt_api.dto.response.UserResponse;
import com.bunny.rtt_api.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and session management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and set refresh cookie")
    public ResponseEntity<SessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        SessionResponse session = authService.login(request, servletRequest, servletResponse);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue new access token")
    public ResponseEntity<SessionResponse> refresh(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        SessionResponse session = authService.refresh(servletRequest, servletResponse);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear refresh token cookie and invalidate session")
    public ResponseEntity<Void> logout(HttpServletResponse servletResponse) {
        authService.logout(servletResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<UserResponse> getMe() {
        UserResponse user = authService.getMe();
        return ResponseEntity.ok(user);
    }
}
