-- V2: Add issued-credential & authentication management columns to users table
-- Supports 24-hour first login expiry, 14-day issued expiry, rate-limiting lockout, and FCM tokens.

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(255),
    ADD COLUMN IF NOT EXISTS role VARCHAR(32) DEFAULT 'demo',
    ADD COLUMN IF NOT EXISTS label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS first_login_at TIMESTAMPTZ(3),
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ(3),
    ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMPTZ(3),
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ(3),
    ADD COLUMN IF NOT EXISTS attempt_count INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ(3);
