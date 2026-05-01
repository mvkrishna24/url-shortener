-- Supports the device-breakdown analytics query which groups and counts by device_type.
-- Partial index excludes NULL and 'unknown' rows that contribute no meaningful signal.
CREATE INDEX idx_clicks_device_type
    ON clicks(device_type)
    WHERE device_type IS NOT NULL AND device_type <> 'unknown';
