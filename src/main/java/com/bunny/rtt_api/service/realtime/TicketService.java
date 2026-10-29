package com.bunny.rtt_api.service.realtime;

import com.bunny.rtt_api.config.CacheConfig;
import com.bunny.rtt_api.dto.response.RealtimeTicketResponse;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.exception.AppException;
import com.bunny.rtt_api.exception.ErrorCode;
import com.bunny.rtt_api.security.TokenService;
import io.jsonwebtoken.Claims;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    private static final int TICKET_TTL_SECONDS = 30;

    private final TokenService tokenService;
    private final CacheManager cacheManager;

    public TicketService(TokenService tokenService, CacheManager cacheManager) {
        this.tokenService = tokenService;
        this.cacheManager = cacheManager;
    }

    public RealtimeTicketResponse createTicket(User user) {
        String ticket = tokenService.generateWsTicket(user.getId().toString(), TICKET_TTL_SECONDS);
        Claims claims = tokenService.parseAccessToken(ticket);
        String jti = claims.getId();

        Cache cache = cacheManager.getCache(CacheConfig.CACHE_WS_TICKETS);
        if (cache != null) {
            cache.put(jti, user.getId().toString());
        }

        return new RealtimeTicketResponse(ticket, TICKET_TTL_SECONDS);
    }

    public String consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Missing ticket");
        }

        try {
            Claims claims = tokenService.parseAccessToken(ticket);
            String type = claims.get("type", String.class);
            if (!"ws_ticket".equals(type)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid ticket type");
            }

            String jti = claims.getId();
            Cache cache = cacheManager.getCache(CacheConfig.CACHE_WS_TICKETS);
            if (cache == null) {
                throw new AppException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Cache unavailable");
            }

            String userId = cache.get(jti, String.class);
            if (userId == null) {
                throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Ticket expired or already used");
            }

            // Single-use eviction
            cache.evict(jti);
            return userId;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid ticket: " + e.getMessage());
        }
    }
}
