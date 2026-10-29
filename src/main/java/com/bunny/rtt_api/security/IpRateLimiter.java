package com.bunny.rtt_api.security;

import com.bunny.rtt_api.exception.AppException;
import com.bunny.rtt_api.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class IpRateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    private final Cache<String, WindowCounter> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(10_000)
            .build();

    private static class WindowCounter {
        Instant windowStart = Instant.now();
        int count = 0;
    }

    public synchronized void checkRateLimit(HttpServletRequest request) {
        String ip = getClientIp(request);
        Instant now = Instant.now();

        WindowCounter counter = cache.get(ip, k -> new WindowCounter());
        if (counter.windowStart.plusSeconds(60).isBefore(now)) {
            counter.windowStart = now;
            counter.count = 0;
        }

        counter.count++;
        if (counter.count > MAX_REQUESTS_PER_MINUTE) {
            throw new AppException(
                    ErrorCode.RATE_LIMITED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Maximum 10 requests per minute."
            );
        }
    }

    public void reset(String ip) {
        cache.invalidate(ip);
    }

    public static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
