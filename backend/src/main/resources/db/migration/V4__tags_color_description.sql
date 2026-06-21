-- ============================================================
-- V4: Add color and description columns to tags table
-- Both fields exist in the Tag entity but were absent from V1.
-- Hibernate validate mode fails at startup when columns are missing.
-- ============================================================

ALTER TABLE tags
    ADD COLUMN IF NOT EXISTS color       VARCHAR(50)  NOT NULL DEFAULT '#3B82F6',
    ADD COLUMN IF NOT EXISTS description TEXT;
