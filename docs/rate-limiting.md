# Rate Limiting Deep-Dive

Pre-interview reference for the sliding-window-log implementation in this service.

---

## Algorithm choice: why sliding window log?

Three common rate-limiting algorithms, compared:

### Fixed window counter
Split time into discrete 1-minute buckets. Increment a counter per bucket.

**Problem — boundary burst**: a client can send 100 requests at 00:59 and 100 more at 01:01. Both fit within their respective windows, but 200 requests land in 2 seconds — double the intended rate.

### Token bucket / leaky bucket
Tokens refill at a steady rate; each request consumes one token.

**Good for**: smoothing bursty traffic, allowing short bursts up to the bucket capacity.  
**Drawback**: state is a (tokens, last-refill-time) tuple — simple to implement but harder to reason about at the exact boundary. Also non-trivial to make atomic in a distributed system without Lua.

### Sliding window log ← what we use
Store a timestamped log of every request in a sorted set. At each request:
1. Remove entries older than `now - window`.
2. Count remaining entries.
3. If count ≥ limit → reject; otherwise admit and record.

**Why we chose it**:
- Exact: no boundary burst possible. The window slides continuously with the clock.
- Natural fit for Redis sorted sets: eviction is a single `ZREMRANGEBYSCORE`, count is `ZCARD`.
- Easy to audit: the sorted set IS the request log.

**Trade-off**: memory per client = O(limit). At 100 req/min per IP with short codes as values, each entry is ~20 bytes. 1 000 simultaneous IPs × 100 entries × 20 bytes ≈ 2 MB. Negligible.

---

## Why a Lua script for atomicity

The naive multi-step approach:

```
ZREMRANGEBYSCORE key -inf (now - window)   # 1. evict old
count = ZCARD key                           # 2. count
if count < limit:
    ZADD key now <member>                   # 3. admit
```

Without atomicity, between steps 2 and 3, **two concurrent requests** can both read `count = N-1`, both conclude they are under the limit, and both write. Result: N+1 requests admitted when only N were allowed — a classic TOCTOU race.

**Redis Lua scripts run single-threaded**. The entire script executes without interruption; no other command can interleave. This eliminates the race with zero locking overhead.

`RedisScript.of(Resource, List.class)` computes the SHA1 of the script at startup. Subsequent calls use `EVALSHA` — the script is cached server-side, so only the 20-byte hash travels on the wire after the first invocation.

### Lua script walkthrough (`scripts/sliding_window.lua`)

```lua
-- Remove timestamps outside the window (scored below now - window)
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

-- Count surviving entries
local count = tonumber(redis.call('ZCARD', key))

-- On deny: find when the oldest slot reopens
if count >= limit then
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    reset = math.ceil((tonumber(oldest[2]) + window) / 1000)
    return {0, 0, reset}
end

-- Admit: record this request with a unique member
-- Member = "<timestamp>:<count>" — unique because:
--   same script, sequential execution → each call sees a different count
redis.call('ZADD', key, now, tostring(now) .. ':' .. tostring(count))
redis.call('EXPIRE', key, ttl)

return {1, limit - count - 1, math.ceil((now + window) / 1000)}
```

---

## Why Redis sorted sets are perfect for this

| Operation | Redis command | Complexity | What it does |
|---|---|---|---|
| Evict old entries | `ZREMRANGEBYSCORE` | O(log N + M) | Remove all members with score < window start |
| Count active entries | `ZCARD` | O(1) | Exact count in the sorted set |
| Record a request | `ZADD` | O(log N) | Insert with timestamp as score |
| Find oldest entry | `ZRANGE key 0 0` | O(log N) | Lowest-scored member |

Sorted sets maintain order by score (timestamp) automatically. No manual sorting, no scanning.

---

## X-Forwarded-For and reverse proxy trust

Behind nginx or an AWS ALB, the reverse proxy appends the real client IP:

```
X-Forwarded-For: 203.0.113.42, 10.0.0.1
                 ^client IP    ^proxy IP
```

We take `split(",")[0].trim()` — the leftmost value.

**The trust problem**: if there is NO trusted proxy in front of the app, a malicious client can send:

```
X-Forwarded-For: 1.2.3.4
```

...and be rate-limited under the fake IP `1.2.3.4` instead of their real one. They can trivially rotate fake IPs and bypass the limit.

**Mitigation in production**:
- Configure nginx/ALB to **overwrite** (not append) `X-Forwarded-For` with the real client IP before forwarding. The app then trusts the single value because the proxy sanitised it.
- Or use `REMOTE_ADDR` directly when the app is guaranteed to sit behind a single trusted hop.
- Spring Boot supports `server.forward-headers-strategy: NATIVE` or `FRAMEWORK` which reads `X-Forwarded-*` headers and trusts them based on configured trusted proxies.

---

## Response headers

Every response to `/api/v1/**` carries:

| Header | Meaning |
|---|---|
| `X-RateLimit-Limit` | Configured cap for this client tier (100 anon / 1 000 auth per minute) |
| `X-RateLimit-Remaining` | Requests left in the current 1-minute window |
| `X-RateLimit-Reset` | Unix epoch second when the oldest in-window request expires |

On a 429 response, `X-RateLimit-Remaining` is always `0`. `X-RateLimit-Reset` tells the client exactly how many seconds to wait.

---

## Limits by tier

| Tier | Identifier | Limit |
|---|---|---|
| Anonymous | `ip:<clientIp>` | 100 req/min |
| Authenticated | `user:<principalName>` | 1 000 req/min |
| Redirect (`GET /{code}`) | not rate-limited | — |

Redirect endpoints are excluded intentionally: redirect latency is the product's core SLA, and a rate limit on redirects would punish legitimate high-traffic links (e.g. a link shared on Twitter).

---

## Fail-open behaviour

If Redis is unavailable, `RateLimiterService.tryAcquire` catches the exception, logs a warning, and returns `allowed=true`. The service degrades gracefully: rate limiting stops working temporarily, but traffic is never dropped due to an infrastructure hiccup.

---

## Running the load test

```bash
# Install k6: https://k6.io/docs/get-started/installation/
k6 run load-tests/rate-limit.js
```

Expected output: 100 `✓ urls_created`, 20 `✓ requests_rejected_429`, all `X-RateLimit-*` headers present.
