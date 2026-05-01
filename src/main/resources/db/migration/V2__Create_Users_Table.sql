CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Link existing URLs table to the new users table
ALTER TABLE urls ADD COLUMN user_id BIGINT;
ALTER TABLE urls ADD CONSTRAINT fk_urls_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_urls_user_id ON urls(user_id);