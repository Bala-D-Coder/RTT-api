package com.bunny.rtt_api.repository;

import com.bunny.rtt_api.entity.NoteBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NoteBlockRepository extends JpaRepository<NoteBlock, UUID> {
    List<NoteBlock> findByNoteIdOrderByPositionAsc(UUID noteId);
    List<NoteBlock> findBySourceOrderByCreatedAtDesc(String source);
}
