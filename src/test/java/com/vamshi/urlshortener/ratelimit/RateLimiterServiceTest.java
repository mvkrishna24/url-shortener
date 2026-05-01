package com.vamshi.urlshortener.ratelimit;

import com.vamshi.urlshortener.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the Lua script execution via real Testcontainers Redis.
 * Each test uses a unique identifier so tests are fully isolated without flushing.
 */
class RateLimiterServiceTest extends AbstractIntegrationTest {

    @Autowired RateLimiterService rateLimiterService;
    @Autowired StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void flushRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys(RateLimiterService.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void firstRequest_underLimit_isAllowed() {
        AcquireResult result = rateLimiterService.tryAcquire("test-first", 5, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
    }

    @Test
    void atLimit_nextRequest_isDenied() {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.tryAcquire("test-at-limit", 5, Duration.ofMinutes(1));
        }

        AcquireResult denied = rateLimiterService.tryAcquire("test-at-limit", 5, Duration.ofMinutes(1));

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isEqualTo(0);
    }

    @Test
    void remaining_decrementsByOnePerRequest() {
        rateLimiterService.tryAcquire("test-decrement", 5, Duration.ofMinutes(1));
        rateLimiterService.tryAcquire("test-decrement", 5, Duration.ofMinutes(1));
        AcquireResult third = rateLimiterService.tryAcquire("test-decrement", 5, Duration.ofMinutes(1));

        assertThat(third.remaining()).isEqualTo(2);
    }

    @Test
    void resetEpochSeconds_isInTheFuture() {
        AcquireResult result = rateLimiterService.tryAcquire("test-reset-epoch", 5, Duration.ofMinutes(1));
        long nowSeconds = System.currentTimeMillis() / 1000;

        assertThat(result.resetEpochSeconds()).isGreaterThan(nowSeconds);
        assertThat(result.resetEpochSeconds()).isLessThanOrEqualTo(nowSeconds + 61);
    }

    @Test
    void differentIdentifiers_haveIndependentCounters() {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.tryAcquire("test-sep-a", 5, Duration.ofMinutes(1));
        }

        // user-a is exhausted, user-b is untouched
        AcquireResult a = rateLimiterService.tryAcquire("test-sep-a", 5, Duration.ofMinutes(1));
        AcquireResult b = rateLimiterService.tryAcquire("test-sep-b", 5, Duration.ofMinutes(1));

        assertThat(a.allowed()).isFalse();
        assertThat(b.allowed()).isTrue();
        assertThat(b.remaining()).isEqualTo(4);
    }

    @Test
    void lastAllowedRequest_hasRemainingZero() {
        for (int i = 0; i < 4; i++) {
            rateLimiterService.tryAcquire("test-last-allowed", 5, Duration.ofMinutes(1));
        }
        AcquireResult last = rateLimiterService.tryAcquire("test-last-allowed", 5, Duration.ofMinutes(1));

        assertThat(last.allowed()).isTrue();
        assertThat(last.remaining()).isEqualTo(0);
    }
}
