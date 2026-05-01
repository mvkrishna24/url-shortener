/**
 * Sliding-window rate limiting backed by Redis.
 *
 * <p>{@code RateLimiterService} executes a Lua script atomically against Redis to maintain
 * per-IP request counts within a rolling 60-second window. {@code RateLimitInterceptor}
 * applies this check to every inbound HTTP request and rejects excess traffic with
 * {@code 429 Too Many Requests}, returning standard {@code X-RateLimit-*} headers.
 */
package com.vamshi.urlshortener.ratelimit;
