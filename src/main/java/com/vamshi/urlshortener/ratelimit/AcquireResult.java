package com.vamshi.urlshortener.ratelimit;

/**
 * Immutable result returned by {@link RateLimiterService#tryAcquire}.
 *
 * @param allowed          true if the request is within quota
 * @param limit            configured cap for this identifier (for response headers)
 * @param remaining        requests still available in the current window; 0 when denied
 * @param resetEpochSeconds Unix epoch second when the current window resets
 */
public record AcquireResult(boolean allowed, int limit, int remaining, long resetEpochSeconds) {}
