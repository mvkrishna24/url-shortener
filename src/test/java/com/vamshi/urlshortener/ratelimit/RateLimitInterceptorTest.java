package com.vamshi.urlshortener.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for client-IP extraction logic.
 * No Spring context needed — tests the static helper directly.
 */
class RateLimitInterceptorTest {

    @Test
    void noForwardedHeader_usesRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(RateLimitInterceptor.extractClientIp(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void xForwardedFor_singleValue_usesIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.42");

        assertThat(RateLimitInterceptor.extractClientIp(request)).isEqualTo("203.0.113.42");
    }

    @Test
    void xForwardedFor_multipleValues_takesLeftmost() {
        // Format: <client>, <proxy1>, <proxy2>
        // Leftmost is the original client IP as appended by the first trusted proxy
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.42, 10.0.0.1, 172.16.0.1");

        assertThat(RateLimitInterceptor.extractClientIp(request)).isEqualTo("203.0.113.42");
    }

    @Test
    void xForwardedFor_withWhitespace_isTrimmed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "  203.0.113.99 , 10.0.0.2");

        assertThat(RateLimitInterceptor.extractClientIp(request)).isEqualTo("203.0.113.99");
    }

    @Test
    void xForwardedFor_blank_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("192.168.1.5");

        assertThat(RateLimitInterceptor.extractClientIp(request)).isEqualTo("192.168.1.5");
    }
}
