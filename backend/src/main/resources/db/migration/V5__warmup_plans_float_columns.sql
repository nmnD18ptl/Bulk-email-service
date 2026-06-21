-- ============================================================
-- V5: Fix column types in warmup_plans
-- V1 defined bounce_rate_threshold and complaint_rate_threshold
-- as DECIMAL(5,2) which PostgreSQL stores as NUMERIC.
-- The WarmupPlan entity uses Java Double, which Hibernate maps
-- to float(53) / DOUBLE PRECISION. Hibernate validate mode
-- rejects the NUMERIC vs float(53) mismatch at startup.
-- ============================================================

ALTER TABLE warmup_plans
    ALTER COLUMN bounce_rate_threshold    TYPE DOUBLE PRECISION
        USING bounce_rate_threshold::DOUBLE PRECISION,
    ALTER COLUMN complaint_rate_threshold TYPE DOUBLE PRECISION
        USING complaint_rate_threshold::DOUBLE PRECISION;
