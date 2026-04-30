-- ============================================================
-- V4: Batch-allocation sequence for URL IDs
--
-- WHY INCREMENT BY 1000 (not 1)?
--   With INCREMENT BY 1, every URL creation requires a DB round-trip to call nextval().
--   At 10K req/s that is 10K sequence fetches per second — a bottleneck.
--
--   With INCREMENT BY 1000, one nextval() call reserves a block of 1000 IDs.
--   The app instance hands out those IDs in-memory (AtomicLong, no lock, no DB).
--   At 10K req/s: ~10 sequence fetches per second instead of 10K.
--   That is a 1000× reduction in DB round-trips per unit time.
--
-- TRADE-OFF: if an app instance crashes mid-block, up to 1000 IDs are permanently
--   skipped. This is intentional and acceptable:
--   - Short codes are guaranteed unique regardless of gaps.
--   - At a sequence this large, skipped IDs are invisible to users.
--   - The alternative (smaller block) would restore the throughput problem.
--
-- WHY CACHE 1 at the sequence level?
--   We are already caching 1000 IDs at the application level — there is no benefit
--   to also pre-fetching at the PostgreSQL level. CACHE 1 ensures each nextval()
--   truly reserves a fresh block and prevents double-allocation across server restarts.
-- ============================================================

CREATE SEQUENCE url_id_seq
    START WITH 1
    INCREMENT BY 1000
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

COMMENT ON SEQUENCE url_id_seq IS
    'Block-allocated counter. Each nextval() call reserves 1000 IDs for one app instance. '
    'IdGeneratorService fetches blocks and dispenses IDs in-memory via AtomicLong CAS.';
