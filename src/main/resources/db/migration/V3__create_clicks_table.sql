-- ============================================================
-- V3: Clicks table
--
-- WHY ON DELETE CASCADE for url_id?
--   A URL is deleted → its click analytics have no meaning without the URL.
--   Cascade-delete keeps the DB clean with no orphaned rows.
--   (Contrast with urls.user_id which uses SET NULL to keep links alive.)
--
-- WHY ip_address TEXT and not INET?
--   PostgreSQL's INET type is excellent for subnet operations (>>= operator etc.)
--   but creates a JDBC type-mapping problem with Hibernate: Hibernate maps Java
--   String → VARCHAR, and PostgreSQL rejects VARCHAR for an INET column.
--   A custom Hibernate AttributeConverter (using PGobject) is needed to bridge
--   the gap — added complexity for a field that's just stored/retrieved, never queried
--   with subnet operators in this service. TEXT is pragmatic here.
--   If you need subnet queries later:
--     ALTER TABLE clicks ALTER COLUMN ip_address TYPE INET USING ip_address::INET;
-- ============================================================

CREATE TABLE clicks (
    id           BIGSERIAL   PRIMARY KEY,
    url_id       BIGINT      NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    clicked_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    country_code VARCHAR(2),           -- ISO 3166-1 alpha-2 (e.g. 'US', 'IN')
    referrer     TEXT,                 -- full Referer header value, can be long
    device_type  VARCHAR(20),          -- 'mobile' | 'desktop' | 'tablet' | 'bot'
    ip_address   TEXT                  -- see note above; never exposed via API
);

-- WHY a COMPOSITE INDEX on (url_id, clicked_at DESC)?
--
-- The primary analytics query is a time-series dashboard:
--   SELECT * FROM clicks WHERE url_id = ? ORDER BY clicked_at DESC LIMIT 100
--
-- Without an index:
--   1. Seq scan on clicks (millions of rows)
--   2. Filter: url_id = X (keeps maybe 0.01%)
--   3. Sort remaining rows by clicked_at DESC
--
-- With this composite index:
--   1. Index scan on (url_id, clicked_at DESC) — directly jumps to url_id = X
--   2. Rows already come out sorted DESC — NO separate sort step
--   PostgreSQL's EXPLAIN ANALYZE will show "Index Scan" with no "Sort" node.
--
-- WHY clicked_at DESC in the index definition?
--   B-tree indexes are bidirectional by default for single columns, but for
--   composite indexes the sort direction is baked in. Declaring DESC means
--   the index leaf pages are laid out newest-first for each url_id, so
--   a LIMIT 100 query reads exactly 100 pages and stops — no heap sort needed.
CREATE INDEX idx_clicks_url_id_clicked_at ON clicks (url_id, clicked_at DESC);

COMMENT ON TABLE  clicks            IS 'Immutable click events. Written asynchronously to avoid blocking HTTP 302 redirects.';
COMMENT ON COLUMN clicks.ip_address IS 'Raw client IP after X-Forwarded-For parsing. Never returned by any API endpoint.';
