package com.vamshi.urlshortener.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Sliding-window-log rate limiter backed by a Redis sorted set.
 *
 * <p><b>Why sorted sets?</b><br>
 * Each request is stored as a member scored by its timestamp (ms). ZREMRANGEBYSCORE
 * evicts old entries in O(log N + M) where M is evicted entries. ZCARD gives the
 * current window count in O(1). No sliding needed at read time — the set IS the log.
 *
 * <p><b>Why a Lua script?</b><br>
 * The ZREMRANGEBYSCORE → ZCARD → ZADD sequence must be atomic. Without atomicity,
 * two concurrent requests can both read count=N-1 (under limit), both decide to admit
 * themselves, and both write — resulting in N+1 requests admitted when only N were
 * allowed. Redis executes Lua scripts single-threaded; no locks, no races.
 *
 * <p><b>Fail-open policy</b><br>
 * If Redis is unavailable, {@code tryAcquire} returns {@code allowed=true} and logs
 * a warning. This is intentional: a rate-limiter outage should not take down the
 * service. Accept some excess traffic rather than drop all traffic.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    public static final String KEY_PREFIX = "ratelimit:";

    // RedisScript.of(Resource) computes the SHA1 hash once at startup.
    // Subsequent calls use EVALSHA (script caching on the Redis server side),
    // saving network bandwidth vs sending the full Lua text every time.
    private static final RedisScript<List> SLIDING_WINDOW_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/sliding_window.lua"), List.class);

    private final StringRedisTemplate redis;
    private final Counter rejectedCounter;

    public RateLimiterService(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.rejectedCounter = Counter.builder("urlshortener.ratelimit.rejected")
                .description("Number of requests rejected by the rate limiter")
                .register(meterRegistry);
    }

    /**
     * Attempts to admit one request for {@code identifier} within {@code window}.
     *
     * @param identifier client key suffix (e.g. "ip:1.2.3.4" or "user:alice")
     * @param limit      max requests allowed per window
     * @param window     rolling time window
     * @return result carrying {@code allowed}, remaining quota, and reset epoch
     */
    public AcquireResult tryAcquire(String identifier, int limit, Duration window) {
        long nowMs      = System.currentTimeMillis();
        long windowMs   = window.toMillis();
        long ttlSeconds = window.toSeconds() + 1; // +1 s so the key outlives the window

        try {
            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) redis.execute(
                    SLIDING_WINDOW_SCRIPT,
                    List.of(KEY_PREFIX + identifier),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    String.valueOf(ttlSeconds)
            );

            if (result == null || result.size() < 3) {
                log.warn("Unexpected Lua result for identifier={}; failing open", identifier);
                return failOpen(limit, nowMs, window);
            }

            boolean allowed   = result.get(0) == 1L;
            int     remaining = result.get(1).intValue();
            long    reset     = result.get(2);
            if (!allowed) {
                rejectedCounter.increment();
            }
            return new AcquireResult(allowed, limit, remaining, reset);

        } catch (Exception e) {
            log.warn("Redis rate-limit script failed for {}; failing open: {}", identifier, e.getMessage());
            return failOpen(limit, nowMs, window);
        }
    }

    private AcquireResult failOpen(int limit, long nowMs, Duration window) {
        long reset = nowMs / 1000 + window.toSeconds();
        return new AcquireResult(true, limit, limit - 1, reset);
    }
}
