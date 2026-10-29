package com.bunny.rtt_api.security;

import com.bunny.rtt_api.config.AppProps;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final int accessTtlMinutes;
    private final int refreshTtlDays;

    public TokenService(AppProps appProps) {
        this.accessKey = Keys.hmacShaKeyFor(appProps.jwt().accessSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(appProps.jwt().refreshSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = appProps.jwt().accessTtlMinutes();
        this.refreshTtlDays = appProps.jwt().refreshTtlDays();
    }

    public String generateAccessToken(String userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(accessKey)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshTtlDays, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(refreshKey)
                .compact();
    }

    public String generateWsTicket(String userId, int ttlSeconds) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttlSeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .subject(userId)
                .claim("type", "ws_ticket")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(accessKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parser()
                .verifyWith(refreshKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
