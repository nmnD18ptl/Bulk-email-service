-- ============================================================
-- V1: Initial PostgreSQL schema for Bulk Email Pro
-- Migrated from H2 (ddl-auto:update) — Flyway now owns DDL
-- ============================================================

-- Enable useful extensions
CREATE EXTENSION IF NOT EXISTS "pg_trgm";       -- trigram similarity (ILIKE fast)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";      -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "btree_gin";     -- GIN on composite types

-- ============================================================
-- ORGANIZATIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS organizations (
    id                      BIGSERIAL       PRIMARY KEY,
    name                    VARCHAR(255)    NOT NULL UNIQUE,
    slug                    VARCHAR(255)    NOT NULL UNIQUE,
    plan                    VARCHAR(50)     NOT NULL DEFAULT 'FREE',
    monthly_email_limit     INTEGER         NOT NULL DEFAULT 500,
    emails_sent_this_month  INTEGER         NOT NULL DEFAULT 0,
    max_contacts            INTEGER         NOT NULL DEFAULT 500,
    max_smtp_configs        INTEGER         NOT NULL DEFAULT 1,
    billing_cycle_start     DATE,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL       PRIMARY KEY,
    organization_id BIGINT          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email           VARCHAR(320)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    role            VARCHAR(50)     NOT NULL DEFAULT 'OPERATOR',
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TAGS
-- ============================================================
CREATE TABLE IF NOT EXISTS tags (
    id              BIGSERIAL       PRIMARY KEY,
    organization_id BIGINT          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(120)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, name)
);

-- ============================================================
-- CONTACTS
-- ============================================================
CREATE TABLE IF NOT EXISTS contacts (
    id                  BIGSERIAL       PRIMARY KEY,
    organization_id     BIGINT          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email               VARCHAR(320)    NOT NULL,
    first_name          VARCHAR(200),
    last_name           VARCHAR(200),
    company             VARCHAR(300),
    country             VARCHAR(120),
    phone               VARCHAR(50),
    status              VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    custom_field1       VARCHAR(500),
    custom_field2       VARCHAR(500),
    custom_field3       VARCHAR(500),
    custom_field4       VARCHAR(500),
    custom_field5       VARCHAR(500),
    email_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    mx_record_valid     BOOLEAN         NOT NULL DEFAULT FALSE,
    engagement_score    INTEGER         NOT NULL DEFAULT 0,
    last_opened_at      TIMESTAMP,
    last_clicked_at     TIMESTAMP,
    subscribed_at       TIMESTAMP,
    unsubscribed_at     TIMESTAMP,
    unsubscribe_token   VARCHAR(64)     UNIQUE,
    opt_in_source       VARCHAR(255),
    gdpr_consent        BOOLEAN         NOT NULL DEFAULT FALSE,
    gdpr_consent_date   TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, email)
);

-- ============================================================
-- CONTACT → TAG (many-to-many)
-- ============================================================
CREATE TABLE IF NOT EXISTS contact_tags (
    contact_id  BIGINT  NOT NULL REFERENCES contacts(id)   ON DELETE CASCADE,
    tag_id      BIGINT  NOT NULL REFERENCES tags(id)        ON DELETE CASCADE,
    PRIMARY KEY (contact_id, tag_id)
);

-- ============================================================
-- SMTP CONFIGURATIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS smtp_configs (
    id                  BIGSERIAL       PRIMARY KEY,
    organization_id     BIGINT          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                VARCHAR(255)    NOT NULL,
    host                VARCHAR(255)    NOT NULL,
    port                INTEGER         NOT NULL DEFAULT 587,
    username            VARCHAR(255)    NOT NULL,
    encrypted_password  TEXT            NOT NULL,
    security_type       VARCHAR(20)     NOT NULL DEFAULT 'TLS',
    provider_type       VARCHAR(50)     NOT NULL DEFAULT 'CUSTOM',
    from_name           VARCHAR(255),
    from_email          VARCHAR(320),
    reply_to_email      VARCHAR(320),
    daily_limit         INTEGER         NOT NULL DEFAULT 500,
    hourly_limit        INTEGER         NOT NULL DEFAULT 100,
    sent_today          INTEGER         NOT NULL DEFAULT 0,
    sent_this_hour      INTEGER         NOT NULL DEFAULT 0,
    last_reset_date     DATE,
    last_reset_hour     TIMESTAMP,
    is_default          BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    connection_tested   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- EMAIL TEMPLATES
-- ============================================================
CREATE TABLE IF NOT EXISTS templates (
    id              BIGSERIAL       PRIMARY KEY,
    organization_id BIGINT          REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(255)    NOT NULL,
    category        VARCHAR(100),
    description     TEXT,
    thumbnail_url   VARCHAR(500),
    html_content    TEXT            NOT NULL,
    text_content    TEXT,
    subject         VARCHAR(500),
    is_built_in     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- CAMPAIGNS
-- ============================================================
CREATE TABLE IF NOT EXISTS campaigns (
    id                      BIGSERIAL       PRIMARY KEY,
    organization_id         BIGINT          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                    VARCHAR(255)    NOT NULL,
    subject                 VARCHAR(500),
    preview_text            VARCHAR(255),
    html_content            TEXT,
    text_content            TEXT,
    status                  VARCHAR(50)     NOT NULL DEFAULT 'DRAFT',
    smtp_config_id          BIGINT          REFERENCES smtp_configs(id) ON DELETE SET NULL,
    template_id             BIGINT          REFERENCES templates(id)    ON DELETE SET NULL,
    send_to_all             BOOLEAN         NOT NULL DEFAULT TRUE,
    from_name               VARCHAR(255),
    from_email              VARCHAR(320),
    reply_to_email          VARCHAR(320),
    physical_address        TEXT,
    scheduled_at            TIMESTAMP,
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    total_recipients        INTEGER         NOT NULL DEFAULT 0,
    sent_count              INTEGER         NOT NULL DEFAULT 0,
    delivered_count         INTEGER         NOT NULL DEFAULT 0,
    open_count              INTEGER         NOT NULL DEFAULT 0,
    click_count             INTEGER         NOT NULL DEFAULT 0,
    bounce_count            INTEGER         NOT NULL DEFAULT 0,
    unsubscribe_count       INTEGER         NOT NULL DEFAULT 0,
    complaint_count         INTEGER         NOT NULL DEFAULT 0,
    failed_count            INTEGER         NOT NULL DEFAULT 0,
    batch_size              INTEGER         NOT NULL DEFAULT 100,
    batch_delay_seconds     INTEGER         NOT NULL DEFAULT 60,
    inter_email_delay_ms    INTEGER         NOT NULL DEFAULT 200,
    max_retries             INTEGER         NOT NULL DEFAULT 3,
    track_opens             BOOLEAN         NOT NULL DEFAULT TRUE,
    track_clicks            BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Campaign audience tag filters (ElementCollection)
CREATE TABLE IF NOT EXISTS campaign_tag_filters (
    campaign_id BIGINT  NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    tag_id      BIGINT  NOT NULL
);

-- ============================================================
-- EMAIL QUEUE
-- ============================================================
CREATE TABLE IF NOT EXISTS email_queue (
    id                      BIGSERIAL       PRIMARY KEY,
    organization_id         BIGINT          NOT NULL,
    campaign_id             BIGINT          NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    contact_id              BIGINT          NOT NULL REFERENCES contacts(id)  ON DELETE CASCADE,
    recipient_email         VARCHAR(320)    NOT NULL,
    recipient_name          VARCHAR(400),
    personalized_subject    TEXT,
    personalized_html       TEXT,
    personalized_text       TEXT,
    status                  VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    priority                INTEGER         NOT NULL DEFAULT 5,
    retry_count             INTEGER         NOT NULL DEFAULT 0,
    max_retries             INTEGER         NOT NULL DEFAULT 3,
    tracking_id             VARCHAR(64),
    message_id              VARCHAR(255),
    error_message           TEXT,
    scheduled_at            TIMESTAMP,
    sent_at                 TIMESTAMP,
    last_attempt_at         TIMESTAMP,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- EMAIL TRACKING (immutable event log)
-- ============================================================
CREATE TABLE IF NOT EXISTS email_tracking (
    id              BIGSERIAL       PRIMARY KEY,
    organization_id BIGINT          NOT NULL,
    tracking_id     VARCHAR(64)     NOT NULL,
    campaign_id     BIGINT          REFERENCES campaigns(id)   ON DELETE CASCADE,
    contact_id      BIGINT          REFERENCES contacts(id)    ON DELETE SET NULL,
    queue_id        BIGINT          REFERENCES email_queue(id) ON DELETE SET NULL,
    event_type      VARCHAR(50)     NOT NULL,
    original_url    TEXT,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    country         VARCHAR(100),
    first_open      BOOLEAN         NOT NULL DEFAULT FALSE,
    first_click     BOOLEAN         NOT NULL DEFAULT FALSE,
    event_at        TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- WARMUP PLANS
-- ============================================================
CREATE TABLE IF NOT EXISTS warmup_plans (
    id                          BIGSERIAL       PRIMARY KEY,
    organization_id             BIGINT          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                        VARCHAR(255)    NOT NULL,
    smtp_config_id              BIGINT          REFERENCES smtp_configs(id) ON DELETE SET NULL,
    status                      VARCHAR(50)     NOT NULL DEFAULT 'NOT_STARTED',
    target_daily_volume         INTEGER         NOT NULL DEFAULT 5000,
    current_stage               INTEGER         NOT NULL DEFAULT 1,
    total_stages                INTEGER         NOT NULL DEFAULT 14,
    current_day_volume          INTEGER         NOT NULL DEFAULT 0,
    schedule_json               TEXT,
    bounce_rate_threshold       DECIMAL(5,2)    DEFAULT 2.0,
    complaint_rate_threshold    DECIMAL(5,2)    DEFAULT 0.1,
    start_date                  DATE,
    estimated_completion_date   DATE,
    last_run_date               DATE,
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- APP SETTINGS (table name must match AppSetting entity)
-- ============================================================
CREATE TABLE IF NOT EXISTS settings (
    id              BIGSERIAL       PRIMARY KEY,
    organization_id BIGINT          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    setting_key     VARCHAR(255)    NOT NULL,
    setting_value   TEXT,
    description     TEXT,
    category        VARCHAR(100),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, setting_key)
);

-- ============================================================
-- Row-level updated_at trigger function (shared)
-- ============================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to all mutable tables
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'organizations','users','contacts','smtp_configs',
        'templates','campaigns','email_queue','warmup_plans','settings'
    ] LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS trg_%s_updated_at ON %I;
             CREATE TRIGGER trg_%s_updated_at
             BEFORE UPDATE ON %I
             FOR EACH ROW EXECUTE FUNCTION set_updated_at();',
            t, t, t, t
        );
    END LOOP;
END;
$$;
