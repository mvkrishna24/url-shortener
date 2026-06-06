# Elevator Pitches

## 30-Second Pitch

"I built a production-grade distributed URL shortener in Java and Spring Boot.
In a one-minute k6 benchmark, a single local instance sustained 100 warm-cache
redirects per second at 4.13 millisecond p99 latency with zero errors and a
99.98% Redis hit rate. The project goes beyond CRUD with 1,000-ID Base62 blocks,
an atomic Redis Lua rate limiter, JWT authentication, and an async analytics
pipeline that keeps enrichment off the redirect path."

## 2-Minute Walkthrough

"When a link is created, the API passes through optional JWT authentication and
a two-tier sliding-window rate limiter. The ID generator reserves 1,000 IDs from
a PostgreSQL sequence at a time, dispenses them with an `AtomicLong`, and encodes
them into compact Base62 codes.

The redirect path checks a read-through Redis cache before PostgreSQL. During the
warm-cache benchmark, it recorded 6,019 hits and one miss while sustaining
100.26 requests per second at 4.13ms p99 latency. Redis failures fall back to
PostgreSQL, so the cache improves speed without becoming the source of truth.

Redirects also publish click events into a bounded in-memory queue and return the
302 immediately. A scheduled worker later performs optional GeoIP and user-agent
enrichment and writes batches of up to 500 rows through JDBC. The rate limiter
uses one atomic Lua script, and k6 verified exactly 100 anonymous requests were
accepted before later requests received HTTP 429."

## 5-Minute Deep Dive Outline

1. **Write path:** PostgreSQL sequence blocks reduce ID-generation round trips by up to 1,000x; Base62 keeps codes compact; gaps after crashes are acceptable.
2. **Read path:** Redis is a read-through optimization with a one-hour TTL and graceful PostgreSQL fallback.
3. **Protection:** Redis sorted sets and Lua provide an exact sliding-window log shared by all app instances.
4. **Analytics:** a bounded queue prioritizes redirect availability; JDBC batching handles background click writes.
5. **Operations:** Flyway owns schema changes, Micrometer exposes metrics, Docker Compose runs PostgreSQL and Redis, and Testcontainers covers real-service integration behavior where Docker API compatibility permits it.
