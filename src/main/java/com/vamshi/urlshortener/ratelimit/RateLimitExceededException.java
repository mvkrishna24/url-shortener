package com.vamshi.urlshortener.ratelimit;

/**
 * Thrown by {@link RateLimitInterceptor} when a client exceeds its quota.
 * Carries the {@link AcquireResult} so the exception handler can populate
 * {@code X-RateLimit-*} response headers without a second Redis round-trip.
 */
public class RateLimitExceededException extends RuntimeException {

    private final AcquireResult acquireResult;

    public RateLimitExceededException(AcquireResult acquireResult) {
        super("Rate limit exceeded");
        this.acquireResult = acquireResult;
    }

    public AcquireResult getAcquireResult() {
        return acquireResult;
    }
}
