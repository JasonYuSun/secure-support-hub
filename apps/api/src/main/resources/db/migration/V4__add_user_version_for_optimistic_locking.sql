-- V4: Add optimistic-locking version column to users

ALTER TABLE users
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
