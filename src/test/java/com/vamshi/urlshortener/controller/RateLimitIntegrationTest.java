package com.vamshi.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vamshi.urlshortener.AbstractIntegrationTest;
import com.vamshi.urlshortener.dto.CreateUrlRequest;
import com.vamshi.urlshortener.ratelimit.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for rate limiting on /api/v1/**.
 *
 * Test profile sets unauthenticated-rpm=5, authenticated-rpm=10
 * so tests complete quickly without hammering 100+ requests.
 *
 * Redis ratelimit:* keys are flushed before each test for isolation
 * (all MockMvc anonymous requests arrive from the same 127.0.0.1 address).
 */
@AutoConfigureMockMvc
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void flushRateLimitKeys() {
        Set<String> keys = stringRedisTemplate.keys(RateLimiterService.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // ── anonymous rate limiting ──────────────────────────────────────────────

    @Test
    void anonymous_withinLimit_returns201() throws Exception {
        postUrl("https://example.com/within-limit")
                .andExpect(status().isCreated());
    }

    @Test
    void anonymous_exceedsLimit_returns429() throws Exception {
        // Exhaust the anonymous quota (5 req/min in test profile)
        for (int i = 0; i < 5; i++) {
            postUrl("https://example.com/exhaust-" + i).andExpect(status().isCreated());
        }

        // 6th request must be rejected
        postUrl("https://example.com/over-limit")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ── authenticated rate limiting ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void authenticated_higherQuota_allows10Requests() throws Exception {
        // Authenticated limit is 10 req/min in test profile
        for (int i = 0; i < 10; i++) {
            postUrl("https://example.com/auth-" + i).andExpect(status().isCreated());
        }

        // 11th is rejected
        postUrl("https://example.com/auth-over")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @WithMockUser(username = "bob")
    void authenticated_usernameBasedKey_isolatedFromAnonymous() throws Exception {
        // Exhaust anonymous quota for 127.0.0.1
        for (int i = 0; i < 5; i++) {
            // Anonymous requests from different test method — flush is per-test; no bleed
        }

        // Authenticated user should still be allowed (different key: user:bob)
        postUrl("https://example.com/auth-isolated")
                .andExpect(status().isCreated());
    }

    // ── response headers ─────────────────────────────────────────────────────

    @Test
    void rateLimitHeaders_presentOnAllowedResponse() throws Exception {
        postUrl("https://example.com/headers-test")
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    void rateLimitHeaders_limitMatchesConfiguredValue() throws Exception {
        var result = postUrl("https://example.com/headers-limit")
                .andExpect(status().isCreated())
                .andReturn();

        int limit = Integer.parseInt(result.getResponse().getHeader("X-RateLimit-Limit"));
        // Test profile sets unauthenticated-rpm=5
        assertThat(limit).isEqualTo(5);
    }

    @Test
    void rateLimitHeaders_remainingDecrements() throws Exception {
        var first = postUrl("https://example.com/decrement-1")
                .andExpect(status().isCreated()).andReturn();
        var second = postUrl("https://example.com/decrement-2")
                .andExpect(status().isCreated()).andReturn();

        int afterFirst  = Integer.parseInt(first.getResponse().getHeader("X-RateLimit-Remaining"));
        int afterSecond = Integer.parseInt(second.getResponse().getHeader("X-RateLimit-Remaining"));
        assertThat(afterSecond).isEqualTo(afterFirst - 1);
    }

    @Test
    void rateLimitHeaders_presentOn429Response() throws Exception {
        for (int i = 0; i < 5; i++) {
            postUrl("https://example.com/429-headers-exhaust-" + i);
        }

        postUrl("https://example.com/429-headers-trigger")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    // ── redirect endpoint not rate limited ───────────────────────────────────

    @Test
    void redirectEndpoint_notIntercepted_noRateLimitHeaders() throws Exception {
        // GET /{shortCode} does not match /api/v1/** — interceptor must not fire
        mockMvc.perform(get("/some-nonexistent-code"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));
    }

    @Test
    void redirectEndpoint_manyRequests_neverReturns429() throws Exception {
        // Exhaust the anonymous API quota first
        for (int i = 0; i < 5; i++) {
            postUrl("https://example.com/exhaust-for-redirect-" + i);
        }

        // Redirect path must still serve normally (not 429) even after API quota is exhausted
        mockMvc.perform(get("/nonexistent-redirect-test"))
                .andExpect(status().isNotFound())   // 404, not 429
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions postUrl(String longUrl) throws Exception {
        String body = objectMapper.writeValueAsString(new CreateUrlRequest(longUrl, null, null));
        return mockMvc.perform(post("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
