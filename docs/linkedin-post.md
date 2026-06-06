# LinkedIn Post Draft

I recently finished building a distributed URL shortener, treating it as a
system-design project rather than a simple CRUD app.

A one-minute local k6 benchmark validated **100.26 warm-cache redirects/sec at
4.13ms p99 latency with zero errors**. The benchmark also recorded **6,019 Redis
cache hits and one miss**.

Three trade-offs shaped the design:

**Read-through vs. write-through caching**

Instead of pre-warming Redis for every created link, the application caches a
URL on its first redirect and keeps it for one hour. This focuses cache memory on
requested links while PostgreSQL remains the source of truth. If Redis fails,
redirects gracefully fall back to PostgreSQL.

**UUIDs vs. batch-allocated counters**

UUIDs avoid coordination but create longer short codes. This service reserves
blocks of 1,000 IDs from a PostgreSQL sequence, dispenses them through an
`AtomicLong`, and encodes them into Base62. The trade-off is harmless ID gaps
after a process crash.

**Analytics accuracy vs. redirect latency**

Redirects publish click events to a bounded in-memory queue and return the 302
without waiting for enrichment. A scheduled worker later performs optional
MaxMind GeoIP and user-agent parsing and writes JDBC batches of up to 500 rows.
The trade-off is that queued telemetry can be lost if an instance crashes.

The API also includes JWT authentication, an atomic Redis Lua sliding-window
rate limiter, Flyway migrations, Micrometer/Prometheus metrics, Docker Compose,
and Testcontainers integration coverage.

Tech stack: Java 17, Spring Boot 3, PostgreSQL 16, Redis 7, Docker, and k6.

GitHub: https://github.com/mvkrishna24/url-shortener

#SystemDesign #BackendEngineering #Java #SpringBoot #Redis #PostgreSQL
