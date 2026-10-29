-- V4: Add transcription source provenance to note_blocks table
-- source ∈ ('dictation', 'typed'), default 'typed'.
-- Index accelerates Home's capture timeline and Notes' Voice chip filter.

ALTER TABLE IF EXISTS note_blocks
    ADD COLUMN IF NOT EXISTS source VARCHAR(32) NOT NULL DEFAULT 'typed';

CREATE INDEX IF NOT EXISTS idx_note_blocks_source_created_at ON note_blocks(source, created_at);
