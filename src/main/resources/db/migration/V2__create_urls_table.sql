-- ============================================================
-- V2: URLs table
--
-- WHY BIGSERIAL for id?
--   The id is what gets Base62-encoded into the short_code.
--   BIGSERIAL gives us ~9.2 * 10^18 possible IDs — enough for billions of URLs
--   with a counter-based allocation strategy. We pre-allocate batches in the
--   service layer (e.g. grab IDs 1000–1999 at once) to avoid one DB call per URL.
--
-- WHY ON DELETE SET NULL for user_id?
--   A user deletes their account → their URLs become anonymous but still work.
--   Alternative (ON DELETE CASCADE) would silently kill every link they created —
--   bad UX if those links are embedded in other people's content.
-- ============================================================

CREATE TABLE urls (
    id           BIGSERIAL   PRIMARY KEY,
    short_code   VARCHAR(10) NOT NULL,
    long_url     TEXT        NOT NULL,
    user_id      BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    custom_alias BOOLEAN     NOT NULL DEFAULT FALSE,
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_urls_short_code UNIQUE (short_code)
);

-- Supports dashboard query: "show all URLs for user X, newest first"
--   SELECT * FROM urls WHERE user_id = ? ORDER BY created_at DESC
CREATE INDEX idx_urls_user_id ON urls (user_id);

-- WHY a PARTIAL INDEX (WHERE expires_at IS NOT NULL)?
--   ~80% of URLs will never expire (expires_at IS NULL). A standard index
--   would include all those NULL rows, wasting space and slowing index scans.
--   A partial index only covers the rows where cleanup is actually needed.
--   The background expiry job runs: SELECT id FROM urls WHERE expires_at < NOW()
--   PostgreSQL can use this partial index directly — far fewer pages to scan.
CREATE INDEX idx_urls_expires_at ON urls (expires_at) WHERE expires_at IS NOT NULL;

COMMENT ON TABLE  urls              IS 'Shortened URLs. user_id NULL means anonymous (unauthenticated) creation.';
COMMENT ON COLUMN urls.short_code   IS 'Base62-encoded 7-char code or user-supplied custom alias.';
COMMENT ON COLUMN urls.custom_alias IS 'TRUE = user-provided short_code; FALSE = auto-generated.';
COMMENT ON COLUMN urls.expires_at   IS 'NULL = never expires. Checked by redirect service before serving.';
