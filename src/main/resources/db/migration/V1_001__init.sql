-- V1: Initial schema for Real-time Transcription application
-- House rules: No CHECK constraints, no business NOT NULL constraints,
-- timestamptz(3) for timestamps, created_by is plain text (no relation cascade).

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    name VARCHAR(255),
    password_hash VARCHAR(255),
    timezone VARCHAR(64) DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notes (
    id UUID PRIMARY KEY,
    created_by VARCHAR(255) NOT NULL,
    title VARCHAR(255) DEFAULT '',
    color VARCHAR(32) DEFAULT 'sand',
    pinned BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMPTZ(3),
    purge_at TIMESTAMPTZ(3),
    seed_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS note_blocks (
    id UUID PRIMARY KEY,
    note_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    text TEXT DEFAULT '',
    position INT DEFAULT 0,
    created_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_note_blocks_note_position ON note_blocks(note_id, position);

CREATE TABLE IF NOT EXISTS tags (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE,
    seed_key VARCHAR(255) UNIQUE
);

CREATE TABLE IF NOT EXISTS note_tags (
    note_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag_id)
);

CREATE TABLE IF NOT EXISTS reminders (
    id UUID PRIMARY KEY,
    created_by VARCHAR(255) NOT NULL,
    note_id UUID REFERENCES notes(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    due_at TIMESTAMPTZ(3) NOT NULL,
    timezone VARCHAR(64) DEFAULT 'Asia/Kolkata',
    repeat VARCHAR(32) DEFAULT 'none',
    weekdays INT[],
    status VARCHAR(32) DEFAULT 'pending',
    attempt_count INT DEFAULT 0,
    last_error TEXT,
    seed_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reminders_status_due_at ON reminders(status, due_at);

CREATE TABLE IF NOT EXISTS reminder_deliveries (
    id UUID PRIMARY KEY,
    reminder_id UUID NOT NULL REFERENCES reminders(id) ON DELETE CASCADE,
    channel VARCHAR(32) NOT NULL,
    attempt INT NOT NULL,
    delivered_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reminder_channel_attempt UNIQUE (reminder_id, channel, attempt)
);

CREATE TABLE IF NOT EXISTS asr_cache (
    id UUID PRIMARY KEY,
    audio_sha256 VARCHAR(64) UNIQUE NOT NULL,
    engine VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    text TEXT NOT NULL,
    segments_json JSONB,
    hit_count INT DEFAULT 0,
    created_at TIMESTAMPTZ(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
