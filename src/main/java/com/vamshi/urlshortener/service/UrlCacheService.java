package com.vamshi.urlshortener.service;

import com.vamshi.urlshortener.util.CacheKeys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Read-through Redis cache for short-code → ResolvedUrl mappings.
 *
 * Value format: "{urlId}|{longUrl}" — both fields needed by the redirect and
 * analytics paths respectively. The pipe separator is safe because long URLs
 * are percent-encoded and cannot contain a raw pipe character.
 *
 * TTL: 1 hour. Explicit invalidation on delete keeps stale reads to zero in
 * the normal case; the TTL is the safety net for missed invalidations.
 *
 * Every Redis call is wrapped in a try/catch. Redis failure degrades
 * performance (higher DB load) but never breaks the redirect path.
 */
@Service
public class UrlCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);

    static final String KEY_PREFIX = CacheKeys.URL_PREFIX;
    static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final Counter hitCounter;
    private final Counter missCounter;

    public UrlCacheService(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.hitCounter = Counter.builder("urlshortener.cache.hits")
                .description("Redis cache hits for short-code lookups")
                .register(meterRegistry);
        this.missCounter = Counter.builder("urlshortener.cache.misses")
                .description("Redis cache misses for short-code lookups")
                .register(meterRegistry);
    }

    /**
     * Returns the cached ResolvedUrl for {@code shortCode}, or empty on miss or error.
     * Existing single-field entries written by older code are treated as a miss and
     * evicted lazily via TTL.
     */
    public Optional<ResolvedUrl> get(String shortCode) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + shortCode);
            if (value != null && value.contains(String.valueOf(ResolvedUrl.SEPARATOR))) {
                hitCounter.increment();
                return Optional.of(ResolvedUrl.deserialize(value));
            }
            missCounter.increment();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis GET failed for shortCode={} — falling through to DB: {}", shortCode, e.getMessage());
            missCounter.increment();
            return Optional.empty();
        }
    }

    /**
     * Writes the resolved URL into the cache with a 1-hour TTL.
     * Silently swallows errors so a Redis failure never breaks a DB lookup.
     */
    public void put(String shortCode, ResolvedUrl resolved) {
        try {
            redis.opsForValue().set(KEY_PREFIX + shortCode, resolved.serialize(), TTL);
        } catch (Exception e) {
            log.warn("Redis SET failed for shortCode={}: {}", shortCode, e.getMessage());
        }
    }

    /**
     * Removes the cache entry for {@code shortCode}.
     * Called when a URL is deleted to prevent stale reads during the TTL window.
     */
    public void invalidate(String shortCode) {
        try {
            redis.delete(KEY_PREFIX + shortCode);
            log.debug("Cache invalidated: {}", shortCode);
        } catch (Exception e) {
            log.warn("Redis DELETE failed for shortCode={}: {}", shortCode, e.getMessage());
        }
    }
}
