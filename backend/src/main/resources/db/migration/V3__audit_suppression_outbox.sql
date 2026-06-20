-- ============================================================
-- V3: Audit log, suppression list, and outbox table
-- These are NEW tables required by P1, P8, P10
-- ============================================================

-- ============================================================
-- SUPPRESSION LIST
-- Global and per-org email suppression (bounces, complaints, manual)
-- ============================================================
CREATE TABLE IF NOT EXISTS suppression_list (
    id              BIGSERIAL       PRIMARY KEY,
    organization_id BIGINT          REFERENCES organizations(id) ON DELETE CASCADE,
    email           VARCHAR(320)    NOT NULL,
    reason          VARCHAR(50)     NOT NULL,   -- BOUNCE | COMPLAINT | UNSUBSCRIBE | MANUAL | SPAM_TRAP
    source          VARCHAR(100),               -- e.g. "campaign:42", "webhook:sendgrid", "manual"
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, email)
);

CREATE INDEX IF NOT EXISTS idx_suppression_org_email
    ON suppression_list (organization_id, email);

CREATE INDEX IF NOT EXISTS idx_suppression_email_global
    ON suppression_list (email)
    WHERE organization_id IS NULL;

-- ============================================================
-- AUDIT LOG
-- Immutable ledger — never UPDATE or DELETE rows
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    organization_id BIGINT          NOT NULL,
    user_id         BIGINT,
    action          VARCHAR(100)    NOT NULL,   -- e.g. CONTACT_CREATED, CAMPAIGN_SENT
    entity_type     VARCHAR(100),
    entity_id       BIGINT,
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_org_time
    ON audit_logs (organization_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON audit_logs (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_user
    ON audit_logs (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

-- ============================================================
-- OUTBOX EVENTS (Transactional Outbox Pattern)
-- Guarantees at-least-once delivery to RabbitMQ (P3)
-- Written in the same transaction as the domain change
-- ============================================================
CREATE TABLE IF NOT EXISTS outbox_events (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,   -- e.g. CAMPAIGN, CONTACT
    aggregate_id    BIGINT          NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,   -- e.g. CAMPAIGN_SEND_REQUESTED
    payload         JSONB           NOT NULL,
    organization_id BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',  -- PENDING | PUBLISHED | FAILED
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox_events (created_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

-- ============================================================
-- DOMAIN VERIFICATIONS
-- Tracks SPF / DKIM / DMARC / BIMI verification state (P9)
-- ============================================================
CREATE TABLE IF NOT EXISTS domain_verifications (
    id                      BIGSERIAL       PRIMARY KEY,
    organization_id         BIGINT          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    domain                  VARCHAR(255)    NOT NULL,
    spf_valid               BOOLEAN         DEFAULT FALSE,
    spf_record              TEXT,
    spf_checked_at          TIMESTAMPTZ,
    dkim_valid              BOOLEAN         DEFAULT FALSE,
    dkim_selector           VARCHAR(100),
    dkim_record             TEXT,
    dkim_checked_at         TIMESTAMPTZ,
    dmarc_valid             BOOLEAN         DEFAULT FALSE,
    dmarc_record            TEXT,
    dmarc_checked_at        TIMESTAMPTZ,
    bimi_valid              BOOLEAN         DEFAULT FALSE,
    bimi_record             TEXT,
    bimi_checked_at         TIMESTAMPTZ,
    mta_sts_valid           BOOLEAN         DEFAULT FALSE,
    mta_sts_policy          TEXT,
    mta_sts_checked_at      TIMESTAMPTZ,
    overall_status          VARCHAR(20)     DEFAULT 'UNKNOWN', -- UNKNOWN|POOR|WARNING|GOOD|EXCELLENT
    last_full_check_at      TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, domain)
);

CREATE INDEX IF NOT EXISTS idx_domain_verif_org
    ON domain_verifications (organization_id);

CREATE TRIGGER trg_domain_verifications_updated_at
    BEFORE UPDATE ON domain_verifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
