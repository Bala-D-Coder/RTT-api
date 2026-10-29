package com.bunny.rtt_api.repository;

import com.bunny.rtt_api.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID>, JpaSpecificationExecutor<Note> {
    Optional<Note> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Note> findBySeedKey(String seedKey);
    boolean existsBySeedKey(String seedKey);
    List<Note> findByDeletedAtIsNullOrderByUpdatedAtDesc();
    List<Note> findByDeletedAtIsNotNullOrderByDeletedAtDesc();
    List<Note> findByDeletedAtIsNotNull();
    long countByDeletedAtIsNull();

    List<Note> findByPurgeAtLessThanEqual(java.time.Instant purgeAt, org.springframework.data.domain.Pageable pageable);
}
