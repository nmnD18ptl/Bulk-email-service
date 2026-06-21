-- ============================================================
-- V6: Allow global (system-level) settings with null organization_id
--
-- DataInitializer inserts system defaults (tracking URL, company name, etc.)
-- without an organization on a fresh database. V1 defined organization_id
-- as NOT NULL, causing DataInitializer to crash immediately after startup.
--
-- The AppSetting entity already declares organizationId without
-- nullable=false, so the Java side has always allowed null.
-- ============================================================

-- Step 1: Drop the NOT NULL constraint
ALTER TABLE settings
    ALTER COLUMN organization_id DROP NOT NULL;

-- Step 2: Drop the old unique constraint (it cannot enforce uniqueness
-- for NULL organization_id rows — PostgreSQL treats NULLs as distinct,
-- allowing duplicate global keys under the old constraint).
ALTER TABLE settings
    DROP CONSTRAINT IF EXISTS settings_organization_id_setting_key_key;

-- Step 3: Re-add correct uniqueness rules via partial indexes.
-- Per-org: same key cannot appear twice within a single organization.
CREATE UNIQUE INDEX IF NOT EXISTS uq_settings_org_key
    ON settings (organization_id, setting_key)
    WHERE organization_id IS NOT NULL;

-- Global: same key cannot appear twice in system-level settings.
CREATE UNIQUE INDEX IF NOT EXISTS uq_settings_global_key
    ON settings (setting_key)
    WHERE organization_id IS NULL;
