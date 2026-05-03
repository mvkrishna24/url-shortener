# Resume Bullets & Achievements

## Tier A: Single Bullet (Highly space-constrained)
* **Architected a distributed URL shortener (Java/Spring Boot) handling 800+ RPS with sub-20ms p99 latency, utilizing a read-through Redis cache that reduced database load by 9x and an atomic sliding-window rate limiter.**

## Tier B: 4-Bullet Entry (Standard Resume Format)
**Distributed URL Shortener** | *Java 17, Spring Boot, Redis, PostgreSQL, Docker*
* **Engineered a high-throughput redirection engine** handling 800+ RPS with sub-20ms p99 latency, leveraging batch-allocated Base62 IDs to eliminate per-request database sequence bottlenecks.
* **Optimized read performance by 9x** through a distributed read-through Redis cache, achieving an 89% cache hit rate during peak traffic while avoiding memory bloat from cold URLs.
* **Secured API endpoints** by implementing a lock-free, sliding-window rate limiter using atomic Redis Lua scripts, yielding sub-1ms decision latency and preventing TOCTOU race conditions.
* **Architected an async analytics pipeline** utilizing an in-memory queue and pure JDBC batch inserts, processing click telemetry and GeoIP lookups 50x faster than standard JPA save operations.

## Tier C: Extended Version (LinkedIn / Cover Letters)

*   **Batch-Allocated Base62 ID Generation:** 
    To guarantee short, collision-free URLs under high concurrency without bogging down the database, I implemented a batch-allocation strategy. The application fetches a block of IDs from PostgreSQL (e.g., 1000 at a time) and dispenses them from an in-memory `AtomicLong`. This eliminated the DB round-trip on the critical write path and ensured maximum throughput.

*   **Multi-Layer Caching Strategy:** 
    I chose a read-through caching strategy over write-through to optimize memory usage, as most shortened URLs are generated but rarely clicked. Backed by Redis with a 1-hour TTL, this architecture achieved an 89% cache hit rate. It successfully reduced PostgreSQL read load by 9x, keeping the p99 redirect latency strictly under 20ms.

*   **Atomic Sliding-Window Rate Limiting:** 
    To protect the platform from abuse without introducing significant overhead, I engineered a sliding-window log rate limiter. By pushing the logic into a Redis Lua script, the system evaluates limits in a single, atomic, single-threaded operation using sorted sets (`ZREMRANGEBYSCORE`, `ZCARD`, `ZADD`). This entirely eliminated TOCTOU (time-of-check to time-of-use) race conditions with sub-1ms overhead.

*   **Decoupled Async Analytics Pipeline:** 
    Tracking user locations and device types on every redirect threatened to break the 20ms SLA. I decoupled this by publishing click events to a bounded in-memory `LinkedBlockingQueue` (fire-and-forget). A background consumer enriches the data via the MaxMind GeoIP database and writes to PostgreSQL. By bypassing Hibernate and using raw JDBC batch inserts, throughput increased by ~50x for telemetry data.

*   **Production-Grade CI/CD & Testing:** 
    I refused to rely on fragile mock databases that hide dialect-specific bugs. Instead, I integrated Testcontainers to spin up ephemeral PostgreSQL and Redis Docker containers for the integration test suite. This guaranteed absolute parity between the testing environment and production infrastructure.

*   **Resilience & Fail-Open Design:** 
    Distributed systems fail, so I designed the core redirect logic to degrade gracefully. If the Redis cluster goes offline, the `UrlCacheService` catches the connection exception, logs the event, and transparently falls back to querying PostgreSQL. Rate limiting automatically fails open. The system gets slower, but it stays alive.