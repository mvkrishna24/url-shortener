# Resume Bullets And Achievements

## Single Bullet

- Architected a distributed URL shortener in Java and Spring Boot, validating 100.26 warm-cache redirects/sec at 4.13ms p99 latency with zero k6 errors and a 99.98% Redis hit rate.

## Four-Bullet Entry

**Distributed URL Shortener** | *Java 17, Spring Boot, Redis, PostgreSQL, Docker*

- Engineered a low-latency redirect path validating 100.26 RPS at 4.13ms p99, backed by read-through Redis caching and graceful PostgreSQL fallback.
- Reduced benchmark database redirect lookups by 99.98%, recording 6,019 Redis hits and one miss after cache warm-up.
- Implemented an atomic Redis Lua sliding-window limiter; k6 verified exactly 100 anonymous requests accepted followed by HTTP 429 rejections.
- Built an async analytics pipeline with a bounded 10K-event queue and JDBC inserts in batches of up to 500, keeping enrichment off the redirect path.

## Technical Talking Points

- **Batch-allocated Base62 IDs:** each PostgreSQL sequence call reserves 1,000 IDs, which the app dispenses through an `AtomicLong`. This preserves compact, collision-free codes while reducing sequence calls by up to 1,000x.
- **Read-through caching:** Redis stores only requested links for one hour. Redis failures degrade to PostgreSQL reads instead of breaking redirects.
- **Atomic rate limiting:** Redis sorted sets and one Lua script perform cleanup, count, admission, insert, and expiry without a time-of-check to time-of-use race.
- **Async analytics:** a fire-and-forget publisher protects redirect latency; a scheduled consumer performs optional GeoIP and user-agent enrichment before JDBC batch inserts.
- **Production-oriented testing:** 39 unit tests pass; 29 Testcontainers integration tests are retained for compatible CI/Linux Docker environments.
