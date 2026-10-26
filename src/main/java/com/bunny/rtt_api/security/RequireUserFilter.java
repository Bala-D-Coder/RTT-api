package com.bunny.rtt_api.security;

import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.dto.response.ApiErrorResponse;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.exception.ErrorCode;
import com.bunny.rtt_api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class RequireUserFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequireUserFilter.class);

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AppProps appProps;
    private final ObjectMapper objectMapper;

    public RequireUserFilter(
            TokenService tokenService,
            UserRepository userRepository,
            AppProps appProps,
            ObjectMapper objectMapper
    ) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.appProps = appProps;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        Claims claims;
        try {
            claims = tokenService.parseAccessToken(token);
        } catch (Exception ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
            sendError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), "Invalid or expired token", null);
            return;
        }

        String userIdStr = claims.getSubject();
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            sendError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), "Invalid user id in token", null);
            return;
        }

        // B20 / B21: Re-read User PK row on every request
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            SecurityContextHolder.clearContext();
            sendError(response, HttpStatus.UNAUTHORIZED, ErrorCode.USER_NOT_FOUND.getCode(), "User account not found", null);
            return;
        }

        User user = userOpt.get();

        if (user.getDisabledAt() != null) {
            SecurityContextHolder.clearContext();
            sendError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.CREDENTIALS_DISABLED.getCode(),
                    "These credentials have been disabled. Contact " + appProps.supportEmail() + " for assistance.",
                    null
            );
            return;
        }

        if (user.getExpiresAt() != null && !user.getExpiresAt().isAfter(Instant.now())) {
            SecurityContextHolder.clearContext();
            String timezone = user.getTimezone() != null ? user.getTimezone() : "Asia/Kolkata";
            String formattedDate;
            try {
                formattedDate = DateTimeFormatter.ofPattern("d MMM, HH:mm z")
                        .withZone(ZoneId.of(timezone))
                        .format(user.getExpiresAt());
            } catch (Exception e) {
                formattedDate = user.getExpiresAt().toString();
            }

            String message = "These demo credentials expired on " + formattedDate + ". Contact " + appProps.supportEmail() + " for access.";
            sendError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.CREDENTIALS_EXPIRED.getCode(),
                    message,
                    Map.of("supportEmail", appProps.supportEmail())
            );
            return;
        }

        String role = user.getRole() != null ? user.getRole() : "demo";
        String authority = "ROLE_" + role.toUpperCase();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
        authentication.setDetails(user.getId().toString());

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String code, String message, Object details)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, message, details));
    }
}
