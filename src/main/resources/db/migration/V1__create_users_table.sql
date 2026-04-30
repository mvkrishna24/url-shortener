-- ============================================================
-- V1: Users table
--
-- WHY TIMESTAMPTZ not TIMESTAMP:
--   TIMESTAMP stores a "wall clock" time with no timezone info.
--   If the DB server's timezone ever changes, every timestamp is misinterpreted.
--   TIMESTAMPTZ stores a UTC epoch value; PostgreSQL converts to/from the session timezone
--   on read/write. This means "what time did this user register?" has a single,
--   unambiguous answer no matter where the DB runs.
--   In Java, OffsetDateTime maps to TIMESTAMPTZ; LocalDateTime maps to TIMESTAMP.
--   Always use TIMESTAMPTZ for audit columns.
-- ============================================================

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email)
    -- The UNIQUE constraint implicitly creates a B-tree index on email.
    -- No separate CREATE INDEX needed — that index already serves equality lookups.
);

COMMENT ON TABLE  users              IS 'Registered users. Anonymous URL creation is allowed (user_id nullable in urls).';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash (cost=12). Raw password is never stored.';
COMMENT ON COLUMN users.updated_at    IS 'Updated by Hibernate @UpdateTimestamp on every save.';
