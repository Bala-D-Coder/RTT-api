package com.bunny.rtt_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asr_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "audio_sha256", unique = true, nullable = false)
    private String audioSha256;

    @Column(name = "engine", nullable = false)
    private String engine;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segments_json", columnDefinition = "jsonb")
    private String segmentsJson;

    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private int hitCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
