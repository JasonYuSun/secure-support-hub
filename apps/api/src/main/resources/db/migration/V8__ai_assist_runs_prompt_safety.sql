-- V8: AI-001 Security Fix — Prompt injection audit traceability
--
-- Rationale: The AI-001 fix (prompt injection via promptOverride) changes the
-- input_snapshot column in ai_assist_runs to store only minimal metadata
-- (no raw user-controlled text). To maintain forensic traceability of the
-- original user hint without persisting its content, we add a SHA-256 column.
--
-- raw_hint_sha256: Lowercase hex-encoded SHA-256 digest of the original
-- (pre-sanitization) user promptOverride. NULL when no hint was provided.
-- The hash allows correlation of a run to a specific raw input if needed for
-- a security investigation, without storing the potentially adversarial content.
ALTER TABLE ai_assist_runs ADD COLUMN raw_hint_sha256 VARCHAR(64);

COMMENT ON COLUMN ai_assist_runs.raw_hint_sha256 IS
  'SHA-256 hex digest of the original (pre-sanitization) user promptOverride. '
  'NULL if no hint was provided. Stored for forensic traceability; '
  'the raw content is NOT persisted (AI-001 fix).';
