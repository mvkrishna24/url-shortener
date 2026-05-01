# Cache Key Conventions

## Redis Key Namespace

Keys are grouped by prefix so different concerns don't collide when sharing a Redis instance.

## Keys

| Key pattern | Example | TTL | Written by | Invalidated by |
|---|---|---|---|---|
| `url:{shortCode}` | `url:aB3xY9` | 1 hour | `UrlCacheService.cacheLongUrl` | `UrlCacheService.invalidate` |
| `ratelimit:{identifier}` | `ratelimit:ip:203.0.113.5` | window + 1 s | `RateLimiterService` (Lua) | expires automatically |

### `url:{shortCode}`

Stores the resolved long URL for a given short code.

- **Value**: raw long URL string (e.g. `https://example.com/some/long/path`)
- **TTL**: 1 hour — balances memory pressure against redirect latency. Shortened URLs tend to see burst traffic in the first hours after creation; the hour window covers most of that burst. Rarely-accessed codes expire automatically without manual cleanup.
- **Cache miss path**: `UrlService.resolveShortCode` queries PostgreSQL, writes the result here, then returns it.
- **Expiry check**: expiry validation happens in `UrlService` after the DB read. Expired URLs are never written to cache.

### `ratelimit:{identifier}`

Sorted set used by the sliding-window-log rate limiter.

- **Members**: `"<timestampMs>:<count>"` — unique per admission within an atomic Lua execution
- **Score**: request timestamp in milliseconds (enables ZREMRANGEBYSCORE eviction)
- **TTL**: window size + 1 second (60 s + 1 = 61 s for a 1-minute window)
- **Written by**: `RateLimiterService` Lua script on every admitted request
- **Eviction**: `ZREMRANGEBYSCORE` inside the Lua script removes expired entries automatically; the key itself expires via `EXPIRE` after the window closes
- **Identifier format**:
  - Anonymous: `ip:<clientIp>` (e.g. `ratelimit:ip:203.0.113.5`)
  - Authenticated: `user:<principalName>` (e.g. `ratelimit:user:alice`)

## Adding New Keys

1. Choose a prefix that groups related keys (e.g. `rate:`, `user:`).
2. Add a row to the table above.
3. Keep values as plain strings where possible — avoid JSON blobs in Redis unless structure is genuinely needed.
4. Always set an explicit TTL; never write unbounded keys.
