package com.bunny.rtt_api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_WS_TICKETS = "ws-tickets";
    public static final String CACHE_ASR = "asr-cache";
    public static final String CACHE_IP_RATE_LIMIT = "ip-rate-limit";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Default configuration
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(5000));
        
        // Register specific caches with custom TTLs
        cacheManager.registerCustomCache(CACHE_WS_TICKETS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(2000)
                .build());

        cacheManager.registerCustomCache(CACHE_ASR, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(1000)
                .build());

        cacheManager.registerCustomCache(CACHE_IP_RATE_LIMIT, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(10000)
                .build());

        return cacheManager;
    }
}
