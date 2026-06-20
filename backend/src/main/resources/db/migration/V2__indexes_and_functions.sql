-- ============================================================
-- V2: Indexes, full-text search, and helper functions
-- ============================================================

-- ============================================================
-- CONTACTS — performance indexes
-- ============================================================

-- Primary lookup: org + email (unique constraint covers equality; this covers scans)
CREATE INDEX IF NOT EXISTS idx_contacts_org_status
    ON contacts (organization_id, status);

-- Fast pagination + sort by engagement
CREATE INDEX IF NOT EXISTS idx_contacts_org_engagement
    ON contacts (organization_id, engagement_score DESC);

-- Unsubscribe token lookup (sparse — only non-null rows)
CREATE INDEX IF NOT EXISTS idx_contacts_unsubscribe_token
    ON contacts (unsubscribe_token)
    WHERE unsubscribe_token IS NOT NULL;

-- Full-text search: email + name + company (GIN for fast ILIKE / @@)
CREATE INDEX IF NOT EXISTS idx_contacts_fts
    ON contacts USING GIN (
        to_tsvector('simple',
            COALESCE(email, '')       || ' ' ||
            COALESCE(first_name, '')  || ' ' ||
            COALESCE(last_name, '')   || ' ' ||
            COALESCE(company, '')     || ' ' ||
            COALESCE(country, '')
        )
    );

-- Trigram index for partial-string search (LIKE '%foo%')
CREATE INDEX IF NOT EXISTS idx_contacts_email_trgm
    ON contacts USING GIN (email gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_contacts_name_trgm
    ON contacts USING GIN (
        (COALESCE(first_name,'') || ' ' || COALESCE(last_name,'')) gin_trgm_ops
    );

-- ============================================================
-- EMAIL QUEUE — critical hot-path indexes
-- ============================================================

-- BatchProcessor fetches pending records by campaign
CREATE INDEX IF NOT EXISTS idx_queue_campaign_status
    ON email_queue (campaign_id, status);

-- Tracking pixel / click link lookup
CREATE INDEX IF NOT EXISTS idx_queue_tracking_id
    ON email_queue (tracking_id)
    WHERE tracking_id IS NOT NULL;

-- Pending-only partial index — keeps index small as sent rows accumulate
CREATE INDEX IF NOT EXISTS idx_queue_pending_scheduled
    ON email_queue (scheduled_at, priority)
    WHERE status = 'PENDING';

-- Org-level queries (plan enforcement, stats)
CREATE INDEX IF NOT EXISTS idx_queue_org_status
    ON email_queue (organization_id, status);

-- ============================================================
-- EMAIL TRACKING — analytics queries
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_tracking_tracking_id
    ON email_tracking (tracking_id);

CREATE INDEX IF NOT EXISTS idx_tracking_campaign_event
    ON email_tracking (campaign_id, event_type);

CREATE INDEX IF NOT EXISTS idx_tracking_contact
    ON email_tracking (contact_id);

-- Time-series dashboard queries
CREATE INDEX IF NOT EXISTS idx_tracking_campaign_time
    ON email_tracking (campaign_id, event_at DESC);

-- ============================================================
-- CAMPAIGNS
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_campaigns_org_status
    ON campaigns (organization_id, status);

-- Scheduled campaign poller
CREATE INDEX IF NOT EXISTS idx_campaigns_scheduled
    ON campaigns (scheduled_at)
    WHERE status = 'SCHEDULED' AND scheduled_at IS NOT NULL;

-- ============================================================
-- USERS
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_users_org
    ON users (organization_id);

-- ============================================================
-- TAGS
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_tags_org
    ON tags (organization_id);

-- ============================================================
-- SMTP CONFIGS
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_smtp_org_active
    ON smtp_configs (organization_id, is_active);

-- ============================================================
-- HELPER: full-text contact search function
-- Called from ContactRepository native query
-- ============================================================
CREATE OR REPLACE FUNCTION search_contacts(
    p_org_id    BIGINT,
    p_query     TEXT,
    p_status    TEXT     DEFAULT NULL,
    p_limit     INTEGER  DEFAULT 50,
    p_offset    INTEGER  DEFAULT 0
)
RETURNS TABLE (
    id              BIGINT,
    email           VARCHAR,
    first_name      VARCHAR,
    last_name       VARCHAR,
    company         VARCHAR,
    country         VARCHAR,
    status          VARCHAR,
    created_at      TIMESTAMP,
    rank            FLOAT4
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        c.id, c.email, c.first_name, c.last_name,
        c.company, c.country, c.status, c.created_at,
        ts_rank(
            to_tsvector('simple',
                COALESCE(c.email,'')       || ' ' ||
                COALESCE(c.first_name,'')  || ' ' ||
                COALESCE(c.last_name,'')   || ' ' ||
                COALESCE(c.company,'')
            ),
            plainto_tsquery('simple', p_query)
        ) AS rank
    FROM contacts c
    WHERE c.organization_id = p_org_id
      AND (p_status IS NULL OR c.status = p_status)
      AND (
            p_query IS NULL OR p_query = '' OR
            to_tsvector('simple',
                COALESCE(c.email,'')       || ' ' ||
                COALESCE(c.first_name,'')  || ' ' ||
                COALESCE(c.last_name,'')   || ' ' ||
                COALESCE(c.company,'')
            ) @@ plainto_tsquery('simple', p_query)
            OR c.email ILIKE '%' || p_query || '%'
      )
    ORDER BY rank DESC, c.created_at DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$ LANGUAGE plpgsql STABLE;

-- ============================================================
-- HELPER: campaign stats aggregation view
-- ============================================================
CREATE OR REPLACE VIEW campaign_stats_view AS
SELECT
    q.campaign_id,
    COUNT(*)                                                    AS total_recipients,
    COUNT(*) FILTER (WHERE q.status = 'SENT')                  AS sent_count,
    COUNT(*) FILTER (WHERE q.status = 'FAILED')                AS failed_count,
    COUNT(*) FILTER (WHERE q.status = 'BOUNCED')               AS bounce_count,
    COUNT(*) FILTER (WHERE q.status = 'PENDING')               AS pending_count,
    COUNT(*) FILTER (WHERE q.status = 'CANCELLED')             AS cancelled_count,
    COUNT(DISTINCT t.contact_id) FILTER (
        WHERE t.event_type = 'OPENED' AND t.first_open = TRUE) AS unique_opens,
    COUNT(DISTINCT t.contact_id) FILTER (
        WHERE t.event_type = 'CLICKED' AND t.first_click = TRUE) AS unique_clicks,
    COUNT(*) FILTER (WHERE t.event_type = 'UNSUBSCRIBED')      AS unsubscribes,
    COUNT(*) FILTER (WHERE t.event_type = 'COMPLAINED')        AS complaints
FROM email_queue q
LEFT JOIN email_tracking t ON t.campaign_id = q.campaign_id
GROUP BY q.campaign_id;
