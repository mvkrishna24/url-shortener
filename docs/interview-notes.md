# Interview Notes

Use this as a technical walkthrough reference for backend interviews. It is not
a resume document and does not contain benchmark claims.

## Short Pitch

This is a Spring Boot URL shortener designed around read-heavy traffic. It uses
PostgreSQL as the source of truth, Redis for hot redirects and rate limiting,
JWT for stateless authentication, and an async analytics pipeline so redirects
do not wait on click enrichment or analytics inserts.

## Why Base62?

Base62 uses:

```text
0-9 A-Z a-z
```

It is URL-safe and compact. A numeric database ID can be encoded into a short
string without characters such as `/`, `+`, or `=` that need escaping.

Compared with UUIDs:

- Shorter output.
- Easier to read and share.
- Collision-free when backed by a unique numeric ID.

Tradeoff:

- Sequential IDs are guessable unless additional obfuscation is added.

## Why PostgreSQL?

PostgreSQL is a good fit because the system needs:

- Strong uniqueness constraints for `short_code` and `email`.
- Foreign keys between users, URLs, and clicks.
- Transactions for URL creation.
- SQL aggregation for analytics.
- Sequences for simple ID generation.
- Mature operational tooling.

PostgreSQL remains the source of truth. Redis is an optimization layer, not the
primary database.

## Why Redis?

Redis is used for two different access patterns:

1. Hot redirect cache.
2. Distributed rate limiting.

For redirects:

- A cache hit avoids a PostgreSQL lookup.
- A cache miss falls back to PostgreSQL and then populates Redis.

For rate limiting:

- Sorted sets store timestamps.
- Lua makes the sliding-window check atomic.
- Multiple app instances can share the same limits.

## Why Flyway?

Flyway makes schema changes explicit and repeatable:

- Migrations are plain SQL.
- Ordering is controlled by versioned filenames.
- Checksums detect accidental edits to applied migrations.
- The app can validate schema state on startup.

This is safer than relying on Hibernate to mutate production schema.

## Why JWT?

JWT keeps authentication stateless:

- The server signs a token at login.
- The JWT subject stores the user ID.
- Requests carry `Authorization: Bearer <token>`.
- The app validates the signature and expiration without a session table.

Tradeoffs:

- Token revocation is not built in.
- Refresh tokens are not implemented yet.
- A compromised token is valid until expiration.

## Why Async Analytics?

Redirects are the most latency-sensitive path. Analytics work can involve:

- User-agent parsing.
- GeoIP lookup.
- Database inserts.

Doing that synchronously would add work before returning the 302. Instead, the
redirect controller publishes a click event to a bounded queue and returns the
redirect response. A scheduled consumer drains events and performs batch inserts.

Tradeoff:

- The in-memory queue can lose events on process crash.
- Queue overflow drops analytics events instead of blocking redirects.

## Why Rate Limiting?

The API needs protection from abusive clients creating URLs or repeatedly
calling authenticated endpoints.

The implementation uses a sliding-window log:

```text
ZREMRANGEBYSCORE -> ZCARD -> ZADD -> EXPIRE
```

Lua makes those operations atomic in Redis. This prevents race conditions where
concurrent requests all believe they are under the limit.

Redirects are intentionally excluded from the rate-limit interceptor because
public links can legitimately receive bursts of traffic.

## How Cache Hit And Miss Works

```text
GET /{shortCode}
  |
  +-- Redis GET url:{shortCode}
        |
        +-- hit: return long URL
        |
        +-- miss:
              query PostgreSQL
              check expiration
              Redis SET url:{shortCode}
              return long URL
```

The system uses read-through caching. It does not write every new URL into Redis
at creation time because many created links may never be clicked.

## How This System Scales

Horizontal scaling:

- Run multiple Spring Boot instances.
- Use shared Redis and PostgreSQL.
- JWT validation works across instances because it is stateless.
- Redis rate-limit keys are shared across instances.

Read scaling:

- Redis absorbs hot redirects.
- PostgreSQL handles cold redirects and source-of-truth reads.

Analytics scaling:

- Current implementation uses an in-memory queue per app instance.
- A future version can move click events to Kafka or RabbitMQ for durability and
  independent consumer scaling.

## What Can Fail And How It Is Handled

| Failure | Handling |
|---|---|
| Redis cache down | Redirect falls back to PostgreSQL |
| Redis rate limiter down | API fails open and logs warning |
| Analytics publish fails | Redirect still returns 302 |
| Analytics queue full | Event is dropped and metric increments |
| GeoIP database missing | Country enrichment disabled |
| Duplicate custom alias | Database and service return conflict |
| Expired link | Redirect returns 410 |
| Missing link | Redirect returns 404 |
| Invalid JWT | Protected endpoints return 401 |

## What I Would Improve Next

- Replace the in-memory click queue with a durable broker.
- Add refresh tokens and token revocation.
- Add bulk URL management and soft-delete recovery workflows.
- Add destination safety checks.
- Add dashboard-focused aggregate tables or materialized views.
- Add production alerting for queue depth, Redis failures, DB pool saturation,
  p95/p99 latency, and 5xx rate.
- Add trusted proxy configuration for `X-Forwarded-For`.

## Common Interview Q&A

### Why not use UUIDs for short codes?

UUIDs are much longer than needed for human-friendly links. A Base62-encoded
numeric ID is compact and collision-free when backed by a database sequence.

### What prevents two users from taking the same alias?

The service checks `existsByShortCode`, and the database enforces a unique
constraint on `urls.short_code`. The database constraint is the final protection
against concurrent requests.

### Why not cache on URL creation?

Many created URLs may never be clicked. Read-through caching only stores links
that are actually used, keeping Redis focused on hot data.

### What happens if Redis is down?

Redirect caching degrades to PostgreSQL reads. Rate limiting fails open. This
keeps the core application available, though with reduced protection and higher
database load.

### How do analytics avoid slowing redirects?

Redirects publish a lightweight event to a queue and return the 302. Enrichment
and inserts happen later in the scheduled consumer.

### What is the biggest current production risk?

Analytics events are buffered in memory. A process crash can lose queued events.
For stronger guarantees, use a durable message broker.

### How would you deploy multiple instances?

Put multiple app instances behind a load balancer, share PostgreSQL and Redis,
and externalize analytics events to a broker if click durability matters.

### Why use Flyway if Hibernate can create tables?

Hibernate schema generation is convenient for prototypes but risky for
production. Flyway migrations are explicit, reviewable, ordered, and validated.

### Why is redirect not rate-limited?

Public links can receive legitimate bursts from social media or campaigns.
Rate-limiting redirects could break the product's primary user experience.
