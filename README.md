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
