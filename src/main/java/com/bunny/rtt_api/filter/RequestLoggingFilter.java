package com.bunny.rtt_api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final Pattern KEY_QUERY_PATTERN = Pattern.compile("([?&](?:key|ticket|token)=)[^&]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFRESH_COOKIE_PATTERN = Pattern.compile("(refreshToken=)[^;]+", Pattern.CASE_INSENSITIVE);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = maskUri(request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            String maskedAuth = maskHeader("authorization", request.getHeader("Authorization"));
            String maskedCookie = maskCookie(request.getHeader("Cookie"));

            // Structured JSON-style log line with redacted secrets
            log.info("{\"method\":\"{}\",\"uri\":\"{}\",\"status\":{},\"durationMs\":{},\"auth\":\"{}\",\"cookie\":\"{}\"}",
                    method, uri, status, duration, maskedAuth, maskedCookie);
        }
    }

    public static String maskUri(String uri) {
        if (uri == null) return "";
        return KEY_QUERY_PATTERN.matcher(uri).replaceAll("$1[MASKED]");
    }

    public static String maskHeader(String headerName, String value) {
        if (value == null) return "none";
        if ("authorization".equalsIgnoreCase(headerName)) {
            if (value.toLowerCase().startsWith("bearer ")) {
                return "Bearer [MASKED]";
            }
            return "[MASKED]";
        }
        return value;
    }

    public static String maskCookie(String cookieHeader) {
        if (cookieHeader == null) return "none";
        return REFRESH_COOKIE_PATTERN.matcher(cookieHeader).replaceAll("$1[MASKED]");
    }

    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) return "";
        return "[MASKED]";
    }
}
