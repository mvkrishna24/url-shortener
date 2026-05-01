# URL Shortener

Production-grade distributed URL shortener built with Java 17, Spring Boot 3, Redis, and PostgreSQL.
Targets 10K writes/sec with sub-20ms p99 redirect latency.

## Problem Statement

Most URL shorteners are toy CRUD apps. This project is built to demonstrate real system design depth:
scalable counter allocation, multi-layer caching, async analytics, and rate limiting — the kind of
architecture you'd see in a production service handling millions of requests per day.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.x |
| Primary DB | PostgreSQL 16 |
| Cache / Rate Limit | Redis 7 |
| Auth | JWT (JJWT 0.12) |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Build | Maven 3.9 |
| Containers | Docker & Docker Compose |
| Testing | JUnit 5, Mockito, Testcontainers |

## Running Locally

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Maven 3.9+

### Start infrastructure
```bash
make up          # starts PostgreSQL 16 + Redis 7
```

### Build & run
```bash
make build       # mvn clean package -DskipTests
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Run tests
```bash
make test        # mvn test (Testcontainers spins up its own DB)
```

### Useful endpoints (once running)
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

### Stop infrastructure
```bash
make down
```

## Performance

### Architecture: read-through cache

```
Client → RedirectController → UrlService
                                  │
                          ┌───────▼────────┐
                          │ UrlCacheService │  Redis (TTL 1 h)
                          └───────┬────────┘
                                  │ miss
                          ┌───────▼────────┐
                          │  UrlRepository │  PostgreSQL
                          └────────────────┘
```

Redirects (`GET /{shortCode}`) are served from Redis on cache hit — no database round-trip. On a warm cache the redirect path is a single network hop to Redis (~0.3 ms p99 on local network).

### Why read-through, not write-through?

Write-through populates the cache on every `POST /api/v1/urls`. Most shortened URLs are never clicked, so pre-warming all of them wastes Redis memory. Read-through only caches URLs that are actually resolved, naturally prioritising hot codes.

### TTL choice: 1 hour

Shortened URLs typically see peak traffic in the minutes to hours after creation (shared on social media, email campaigns, etc.). A 1-hour TTL covers that burst without holding cold entries forever. Infrequently accessed codes expire on their own — no eviction policy tuning needed.

### Redis-down resilience

Every `UrlCacheService` operation is wrapped in a try/catch. If Redis is unavailable, `getLongUrl` returns `Optional.empty()`, and `UrlService` falls through to PostgreSQL transparently. The service stays up; it just becomes slower. Once Redis recovers, the cache warms itself again naturally.

### Observability

Cache hit/miss counters are exposed via Micrometer at `/actuator/prometheus`:

```
urlshortener_cache_hits_total
urlshortener_cache_misses_total
```

Hit rate formula: `hits / (hits + misses)`. Target: **≥ 85 %** under normal load (warm cache after first minute of traffic).

## Rate Limiting

### Algorithm: Sliding Window Log via Redis Sorted Sets

Every request to `/api/v1/**` is checked against a per-client quota stored in Redis. Each request is recorded as a scored entry in a sorted set (`score = timestamp in ms`). Old entries are evicted with a single `ZREMRANGEBYSCORE`, and `ZCARD` returns the exact count in O(1) — no scanning.

```
POST /api/v1/urls
        │
        ▼
RateLimitInterceptor.preHandle()
        │
   extract client ID
   (user:<name> if authed, ip:<addr> if anon)
        │
        ▼
RateLimiterService.tryAcquire()   ─── Lua script (atomic) ──▶ Redis sorted set
        │                                ZREMRANGEBYSCORE       key: ratelimit:<id>
        │                                ZCARD
        │                                ZADD (if allowed)
        │
   ┌────┴────┐
allowed?    denied?
   │            │
set headers  throw RateLimitExceededException
return true  → GlobalExceptionHandler → 429 JSON
```

### Tiers

| Client | Identifier | Limit |
|---|---|---|
| Anonymous | `ip:<X-Forwarded-For or RemoteAddr>` | **100 req/min** |
| Authenticated | `user:<principal name>` | **1 000 req/min** |
| `GET /{shortCode}` | not intercepted | unlimited |

### Response Headers

Present on every `/api/v1/**` response (including 429):

```
X-RateLimit-Limit:     100
X-RateLimit-Remaining: 47
X-RateLimit-Reset:     1714521600
```

### Why Lua for atomicity

The ZREMRANGEBYSCORE → ZCARD → ZADD sequence must be atomic. Without it, two concurrent requests can both read `count = limit - 1`, both admit themselves, and overshoot the limit. Redis runs Lua scripts single-threaded — the entire script executes without interruption.

### Load test

```bash
k6 run load-tests/rate-limit.js
```

Sends 120 requests from one IP in a burst. Expected: first 100 return 201, requests 101-120 return 429.

See [docs/rate-limiting.md](docs/rate-limiting.md) for the full algorithm deep-dive.

## Analytics Pipeline

Redirect click processing is strictly decoupled from the web threads to preserve the `< 20ms` SLA.

- **In-Memory Buffer:** Clicks are published to a bounded `LinkedBlockingQueue` (capacity 10,000). 
- **Fail-safe:** If PostgreSQL slows down and the queue fills, events are dropped rather than applying back-pressure to the redirect controller.
- **Enrichment:** A scheduled consumer drains up to 500 events per second, parses the `User-Agent` string via `yauaa`, and resolves `IP Address` to Country Code via the MaxMind GeoIP database.
- **JDBC Batch Insert:** The consumer bypasses JPA and executes a pure JDBC batch insert. Writing 500 records in a single database round-trip is roughly **50x faster** than issuing 500 individual JPA saves.

### Querying Analytics
The platform leverages `date_trunc` and aggregate SQL queries to power the user dashboard, completely bypassing the ORM for analytics reads. 
*(Screenshot placeholder: frontend integration in progress)*
