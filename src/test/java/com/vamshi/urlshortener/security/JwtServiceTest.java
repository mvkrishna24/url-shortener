package com.vamshi.urlshortener.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-chars!!";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiryHours", 1L);
    }

    @Test
    void generateToken_embeds_userId_and_email() {
        String token = jwtService.generateToken(42L, "user@example.com");

        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void isTokenValid_freshToken_returnsTrue() {
        String token = jwtService.generateToken(1L, "a@b.com");

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void getExpirySeconds_returnsConfiguredHoursAsSeconds() {
        assertThat(jwtService.getExpirySeconds()).isEqualTo(3600L);
    }

    @Test
    void extractAllClaims_invalidToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtService.extractAllClaims("not.a.valid.token"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void generateToken_differentUsers_produceDifferentTokens() {
        String tokenA = jwtService.generateToken(1L, "alice@example.com");
        String tokenB = jwtService.generateToken(2L, "bob@example.com");

        assertThat(tokenA).isNotEqualTo(tokenB);
    }
}
