package com.bunny.rtt_api.service.realtime;

import com.bunny.rtt_api.config.CacheConfig;
import com.bunny.rtt_api.entity.AsrCache;
import com.bunny.rtt_api.repository.AsrCacheRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AsrCacheService {

    private final AsrCacheRepository asrCacheRepository;
    private final CacheManager cacheManager;

    public AsrCacheService(AsrCacheRepository asrCacheRepository, CacheManager cacheManager) {
        this.asrCacheRepository = asrCacheRepository;
        this.cacheManager = cacheManager;
    }

    public String computeSha256(byte[] audioBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(audioBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Transactional
    public Optional<AsrCache> lookup(String audioSha256) {
        if (audioSha256 == null || audioSha256.isBlank()) {
            return Optional.empty();
        }

        // Check memory cache first
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_ASR);
        if (cache != null) {
            AsrCache cached = cache.get(audioSha256, AsrCache.class);
            if (cached != null) {
                cached.setHitCount(cached.getHitCount() + 1);
                asrCacheRepository.save(cached);
                return Optional.of(cached);
            }
        }

        // Check database
        Optional<AsrCache> dbResult = asrCacheRepository.findByAudioSha256(audioSha256);
        if (dbResult.isPresent()) {
            AsrCache entry = dbResult.get();
            entry.setHitCount(entry.getHitCount() + 1);
            AsrCache saved = asrCacheRepository.save(entry);

            if (cache != null) {
                cache.put(audioSha256, saved);
            }
            return Optional.of(saved);
        }

        return Optional.empty();
    }

    @Transactional
    public AsrCache put(String audioSha256, String engine, String model, String text, String segmentsJson) {
        AsrCache entry = AsrCache.builder()
                .audioSha256(audioSha256)
                .engine(engine)
                .model(model)
                .text(text)
                .segmentsJson(segmentsJson)
                .hitCount(0)
                .build();

        AsrCache saved = asrCacheRepository.save(entry);

        Cache cache = cacheManager.getCache(CacheConfig.CACHE_ASR);
        if (cache != null) {
            cache.put(audioSha256, saved);
        }

        return saved;
    }
}
