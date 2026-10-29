-- V3: Add bin and partial indexes to notes table
-- Partial index ensures fast query for active (non-deleted) notes ordered by recency.
-- Purge index ensures fast sweep by the recycler task.

CREATE INDEX IF NOT EXISTS idx_notes_active_updated_at ON notes(updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notes_purge_at ON notes(purge_at);
