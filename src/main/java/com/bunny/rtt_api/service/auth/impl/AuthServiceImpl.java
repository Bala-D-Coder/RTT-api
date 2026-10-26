package com.bunny.rtt_api.service.auth.impl;

import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.dto.request.LoginRequest;
import com.bunny.rtt_api.dto.response.SessionResponse;
import com.bunny.rtt_api.dto.response.UserResponse;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.exception.AppException;
import com.bunny.rtt_api.exception.ErrorCode;
import com.bunny.rtt_api.mapper.UserMapper;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.security.IpRateLimiter;
import com.bunny.rtt_api.security.SecurityUtils;
import com.bunny.rtt_api.security.TokenService;
import com.bunny.rtt_api.service.auth.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final IpRateLimiter ipRateLimiter;
    private final UserMapper userMapper;
    private final AppProps appProps;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            IpRateLimiter ipRateLimiter,
            UserMapper userMapper,
            AppProps appProps
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.ipRateLimiter = ipRateLimiter;
        this.userMapper = userMapper;
        this.appProps = appProps;
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public SessionResponse login(LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        // IP rate limiting: max 10/min
        ipRateLimiter.checkRateLimit(servletRequest);

        String email = request.email() != null ? request.email().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Anti-enumeration: if locked, return invalid_credentials (not account_locked)
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        boolean passwordMatches = false;
        try {
            passwordMatches = user.getPasswordHash() != null && passwordEncoder.matches(request.password(), user.getPasswordHash());
        } catch (Exception ignored) {
            passwordMatches = false;
        }

        if (!passwordMatches) {
            int attempts = user.getAttemptCount() + 1;
            user.setAttemptCount(attempts);
            if (attempts >= 5) {
                user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            }
            userRepository.save(user);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Reset strike counters on successful password
        user.setAttemptCount(0);
        user.setLockedUntil(null);

        if (user.getDisabledAt() != null) {
            userRepository.save(user);
            throw new AppException(
                    ErrorCode.CREDENTIALS_DISABLED,
                    HttpStatus.UNAUTHORIZED,
                    "These credentials have been disabled. Contact " + appProps.supportEmail() + " for assistance."
            );
        }

        if (user.getExpiresAt() != null && !user.getExpiresAt().isAfter(Instant.now())) {
            userRepository.save(user);
            String message = formatExpiredMessage(user);
            throw new AppException(
                    ErrorCode.CREDENTIALS_EXPIRED,
                    HttpStatus.UNAUTHORIZED,
                    message,
                    Map.of("supportEmail", appProps.supportEmail())
            );
        }

        // First login shortens expires_at to min(existing, now() + 24h) for standard demo accounts
        if (user.getEmail() != null && user.getEmail().toLowerCase().startsWith("demo-")
                && user.getLastLoginAt() == null && user.getExpiresAt() != null) {
            Instant shortened = Instant.now().plus(24, ChronoUnit.HOURS);
            if (shortened.isBefore(user.getExpiresAt())) {
                user.setExpiresAt(shortened);
            }
            user.setFirstLoginAt(Instant.now());
        } else if (user.getLastLoginAt() == null) {
            user.setFirstLoginAt(Instant.now());
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = tokenService.generateAccessToken(user.getId().toString(), user.getEmail(), user.getRole());
        String refreshToken = tokenService.generateRefreshToken(user.getId().toString());

        setRefreshCookie(servletResponse, refreshToken, servletRequest.isSecure());

        return new SessionResponse(userMapper.toUserResponse(user), accessToken);
    }

    @Override
    @Transactional
    public SessionResponse refresh(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        // IP rate limiting: max 10/min
        ipRateLimiter.checkRateLimit(servletRequest);

        String refreshToken = extractRefreshCookie(servletRequest);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        Claims claims;
        try {
            claims = tokenService.parseRefreshToken(refreshToken);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        UUID userId;
        try {
            userId = UUID.fromString(claims.getSubject());
        } catch (Exception ex) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid user ID in refresh token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "User account not found"));

        if (user.getDisabledAt() != null) {
            throw new AppException(
                    ErrorCode.CREDENTIALS_DISABLED,
                    HttpStatus.UNAUTHORIZED,
                    "These credentials have been disabled. Contact " + appProps.supportEmail() + " for assistance."
            );
        }

        if (user.getExpiresAt() != null && !user.getExpiresAt().isAfter(Instant.now())) {
            String message = formatExpiredMessage(user);
            throw new AppException(
                    ErrorCode.CREDENTIALS_EXPIRED,
                    HttpStatus.UNAUTHORIZED,
                    message,
                    Map.of("supportEmail", appProps.supportEmail())
            );
        }

        // Token rotation: issue fresh access + refresh token
        String newAccessToken = tokenService.generateAccessToken(user.getId().toString(), user.getEmail(), user.getRole());
        String newRefreshToken = tokenService.generateRefreshToken(user.getId().toString());

        setRefreshCookie(servletResponse, newRefreshToken, servletRequest.isSecure());

        return new SessionResponse(userMapper.toUserResponse(user), newAccessToken);
    }

    @Override
    public void logout(HttpServletResponse servletResponse) {
        ResponseCookie cookie = ResponseCookie.from("rtt_refresh", "")
                .httpOnly(true)
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe() {
        String userIdStr = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Authentication required"));

        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));

        return userMapper.toUserResponse(user);
    }

    private String formatExpiredMessage(User user) {
        String timezone = user.getTimezone() != null ? user.getTimezone() : "Asia/Kolkata";
        String formattedDate;
        try {
            formattedDate = DateTimeFormatter.ofPattern("d MMM, HH:mm z")
                    .withZone(ZoneId.of(timezone))
                    .format(user.getExpiresAt());
        } catch (Exception e) {
            formattedDate = user.getExpiresAt().toString();
        }
        return "These demo credentials expired on " + formattedDate + ". Contact " + appProps.supportEmail() + " for access.";
    }

    private void setRefreshCookie(HttpServletResponse response, String token, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from("rtt_refresh", token)
                .httpOnly(true)
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(14))
                .secure(secure)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("rtt_refresh".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
