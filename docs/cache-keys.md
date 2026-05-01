# Cache Key Conventions

## Redis Key Namespace

All keys written by this service share the `url:` prefix to avoid collisions with any future services sharing the same Redis instance.

## Keys

| Key pattern | Example | TTL | Written by | Invalidated by |
|---|---|---|---|---|
| `url:{shortCode}` | `url:aB3xY9` | 1 hour | `UrlCacheService.cacheLongUrl` | `UrlCacheService.invalidate` |

### `url:{shortCode}`

Stores the resolved long URL for a given short code.

- **Value**: raw long URL string (e.g. `https://example.com/some/long/path`)
- **TTL**: 1 hour — balances memory pressure against redirect latency. Shortened URLs tend to see burst traffic in the first hours after creation; the hour window covers most of that burst. Rarely-accessed codes expire automatically without manual cleanup.
- **Cache miss path**: `UrlService.resolveShortCode` queries PostgreSQL, writes the result here, then returns it.
- **Expiry check**: expiry validation happens in `UrlService` after the DB read. Expired URLs are never written to cache.

## Adding New Keys

1. Choose a prefix that groups related keys (e.g. `rate:`, `user:`).
2. Add a row to the table above.
3. Keep values as plain strings where possible — avoid JSON blobs in Redis unless structure is genuinely needed.
4. Always set an explicit TTL; never write unbounded keys.
