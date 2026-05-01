CREATE TABLE clicks (
    id BIGSERIAL PRIMARY KEY,
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    clicked_at TIMESTAMPTZ NOT NULL,
    ip_address VARCHAR(45),
    referrer TEXT,
    country_code VARCHAR(2),
    device_type VARCHAR(50)
);

CREATE INDEX idx_clicks_url_id_clicked_at ON clicks(url_id, clicked_at);