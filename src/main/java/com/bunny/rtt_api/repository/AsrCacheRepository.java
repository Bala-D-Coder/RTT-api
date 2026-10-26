package com.bunny.rtt_api.repository;

import com.bunny.rtt_api.entity.AsrCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AsrCacheRepository extends JpaRepository<AsrCache, UUID> {
    Optional<AsrCache> findByAudioSha256(String audioSha256);
    boolean existsByAudioSha256(String audioSha256);
}
