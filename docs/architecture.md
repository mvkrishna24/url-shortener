# Architecture Guide

This document describes the current backend architecture of the URL shortener.
It focuses on how requests move through the system, where state lives, and which
tradeoffs were made.

## High-Level Architecture

```text
                  +----------------------+
Client ---------->| Spring Boot API      |
                  |                      |
                  | Controllers          |
                  | Services             |
                  | JWT Security         |
                  | Rate Limit Intercept |
                  +----------+-----------+
                             |
             +---------------+---------------+
             |                               |
             v                               v
      +-------------+                 +-------------+
      | Redis       |                 | PostgreSQL  |
      | cache       |                 | source of   |
      | rate limit  |                 | truth       |
      +-------------+                 +-------------+
             ^
             |
      +------+------+
      | Click queue |
      | analytics   |
      +-------------+
```

The service is intentionally simple at the infrastructure level:

- PostgreSQL stores users, URL mappings, and click events.
- Redis accelerates redirects and backs distributed rate limits.
- Spring Boot owns HTTP handling, authentication, validation, and orchestration.
- Analytics is asynchronous and lossy under pressure so redirects remain available.

## URL Shortening Flow

Endpoint:

```text
POST /api/v1/urls
```

Authentication is optional. If a valid JWT is present, the URL is associated with
the current user. If no JWT is present, the URL is anonymous.

```text
Client
  |
  | POST /api/v1/urls { longUrl, customAlias?, expiresAt? }
  v
UrlController
  |
  v
UrlService
  |
  +-- validate HTTP/HTTPS URL
  +-- validate custom alias and reserved words
  +-- get next numeric ID from IdGeneratorService
  +-- Base62 encode ID unless custom alias is supplied
  +-- attach owner when authenticated
  v
PostgreSQL urls table
  |
  v
201 Created
```

Important details:

- Custom aliases must be 4 to 10 characters and match the request DTO pattern.
- `short_code` has a unique database constraint.
- Anonymous URLs are allowed because `urls.user_id` is nullable.
- Expiration is checked on redirect.

## ID Generation And Base62

`IdGeneratorService` reserves ID blocks from PostgreSQL sequence `url_id_seq`.
The sequence increments by 1000. Each application instance dispenses IDs from
the current block using an `AtomicLong`.

Tradeoff:

- Fewer database round trips on write-heavy bursts.
- Gaps can occur after process restarts, which is acceptable because short-code
  uniqueness does not require contiguous IDs.

`Base62Encoder` maps numeric IDs into URL-safe characters:

```text
0-9 A-Z a-z
```

This avoids characters such as `/`, `+`, and `=` that require URL encoding.

## Redirect Flow

Endpoint:

```text
GET /{shortCode}
```

```text
Client
  |
  | GET /abc123
  v
RedirectController
  |
  v
UrlService.resolveShortCode
  |
  +-- Redis GET url:{shortCode}
  |     |
  |     +-- hit: return long URL and URL id
  |
  +-- miss: PostgreSQL SELECT by short_code
        |
        +-- validate not expired
        +-- Redis SET with TTL
        +-- return long URL and URL id
  |
  v
ClickEventPublisher.publish(...)
  |
  v
302 Found Location: longUrl
```

The redirect controller wraps analytics publishing in `try/catch`. A failure to
publish analytics is logged and does not change the redirect response.

## Redis Cache Flow

Cache key:

```text
url:{shortCode}
```

Cached value:

```text
{urlId}|{longUrl}
```

The redirect path uses a read-through cache:

```text
request -> Redis lookup
       -> cache hit: return immediately
       -> cache miss: query PostgreSQL, then populate Redis
```

Why read-through instead of write-through:

- Many shortened URLs may never be clicked.
- Populating Redis only on first redirect keeps memory focused on hot links.

Failure behavior:

- Redis `GET`, `SET`, and `DELETE` calls are guarded.
- If Redis fails, redirects fall back to PostgreSQL.

## Rate Limiting Flow

Rate limiting applies to:

```text
/api/v1/**
```

Redirects are excluded:

```text
GET /{shortCode}
```

Flow:

```text
API request
  |
  v
RateLimitInterceptor
  |
  +-- authenticated: key = ratelimit:user:{principal}
  +-- anonymous:     key = ratelimit:ip:{clientIp}
  |
  v
Redis Lua sliding-window script
  |
  +-- allowed: continue
  +-- denied: throw RateLimitExceededException
```

The Lua script performs these steps atomically:

1. Remove entries older than the window.
2. Count current entries.
3. Reject if count is at or above the limit.
4. Otherwise record the request timestamp.
5. Set a TTL on the Redis key.

Failure behavior:

- The limiter fails open if Redis is unavailable.
- This favors availability over strict abuse protection during Redis outages.

## JWT Auth Flow

Signup:

```text
POST /api/v1/auth/signup
  -> normalize email
  -> reject duplicate email
  -> hash password with BCrypt
  -> insert user
```

Login:

```text
POST /api/v1/auth/login
  -> normalize email
  -> verify password
  -> sign JWT with user id as subject and email claim
```

Authenticated request:

```text
Authorization: Bearer <token>
  |
  v
JwtAuthenticationFilter
  |
  +-- parse and verify token
  +-- extract user id
  +-- set Authentication principal to Long userId
```

JWTs are stateless. The current implementation does not include refresh tokens
or token revocation.

## Async Analytics Flow

Redirects publish click events after resolving the URL:

```text
RedirectController
  |
  v
ClickEventPublisher
  |
  v
LinkedBlockingQueue
  |
  v
ClickEventConsumer @Scheduled
  |
  +-- drain batch
  +-- parse user agent
  +-- resolve country if GeoLite2 database exists
  +-- JDBC batch insert into clicks
```

The queue is bounded. When full, events are dropped and a metric is incremented.
This is intentional: dropping analytics is better than blocking redirects.

GeoIP is optional:

- If `GeoLite2-Country.mmdb` is present, country codes can be resolved.
- If not present, clicks are still stored without country enrichment.

## Database Schema

Flyway migrations create and evolve the schema.

### `users`

Stores registered users:

- `id`
- `email`
- `password_hash`
- `created_at`
- `updated_at`

`email` is unique.

### `urls`

Stores short-code mappings:

- `id`
- `short_code`
- `long_url`
- `user_id`
- `custom_alias`
- `expires_at`
- `created_at`

Important constraints and indexes:

- Unique `short_code`.
- Nullable `user_id` for anonymous URLs.
- `ON DELETE SET NULL` from users to urls.
- Index on `user_id`.
- Partial index on `expires_at`.

### `clicks`

Stores redirect analytics:

- `id`
- `url_id`
- `clicked_at`
- `ip_address`
- `referrer`
- `country_code`
- `device_type`

Important constraints and indexes:

- `url_id` references `urls(id)` with `ON DELETE CASCADE`.
- Composite index on `(url_id, clicked_at)`.
- Partial indexes for country and device analytics.

### `url_id_seq`

Sequence used by `IdGeneratorService` for block allocation.

## Failure Handling

| Failure | Behavior |
|---|---|
| Redis cache unavailable | Redirect falls back to PostgreSQL |
| Redis rate-limit script fails | Request is allowed and warning is logged |
| Analytics queue full | Click event is dropped, redirect still succeeds |
| Analytics enrichment fails per event | Enrichment is skipped for that value |
| Unknown short code | `404 Not Found` HTML response |
| Expired short code | `410 Gone` HTML response |
| Invalid JWT | Protected endpoints return `401` |
| Duplicate alias | API returns `409 Conflict` |

## Scalability Discussion

The system is designed around a read-heavy workload:

- Redis absorbs hot redirect traffic.
- PostgreSQL remains authoritative and supports relational analytics queries.
- ID generation avoids a database call on every write after a block is reserved.
- Rate limiting is centralized in Redis so multiple app instances share limits.
- Analytics is decoupled from redirects so CPU-heavy enrichment can be tuned
  separately.

Scaling options:

- Run multiple Spring Boot instances behind a load balancer.
- Share Redis and PostgreSQL across instances.
- Move analytics events from in-memory queue to a durable broker.
- Add read replicas or materialized aggregate tables for analytics-heavy dashboards.
- Add monitoring and alerting for Redis latency, database pool saturation, queue
  depth, and HTTP p95/p99 latency.

## Tradeoffs

| Decision | Benefit | Cost |
|---|---|---|
| PostgreSQL sequence IDs | Simple, collision-free | IDs are predictable without extra obfuscation |
| Block allocation | Fewer DB round trips | Gaps after restarts |
| Redis read-through cache | Fast hot redirects | Cold links still hit PostgreSQL |
| In-memory analytics queue | Redirects stay fast | Events can be lost on crash |
| JWT auth | Stateless API | No server-side revocation yet |
| Fail-open rate limiting | Availability during Redis outage | Abuse protection temporarily weakens |
