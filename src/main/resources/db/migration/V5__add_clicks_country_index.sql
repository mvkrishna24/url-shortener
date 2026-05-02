-- Partial index: only index rows where country_code is NOT NULL.
-- The top-countries analytics query filters and groups on country_code;
-- rows with NULL country_code (unresolved IPs) are excluded from that query.
CREATE INDEX idx_clicks_country_code
    ON clicks(country_code)
    WHERE country_code IS NOT NULL;
