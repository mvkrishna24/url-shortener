package com.vamshi.urlshortener.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiry-hours:1}")
    private long expiryHours;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String email) {
        long expiryMillis = expiryHours * 3_600_000L;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMillis))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return (String) extractAllClaims(token).get("email");
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(extractAllClaims(token).getSubject());
    }

    public boolean isTokenValid(String token) {
        // The parser automatically throws ExpiredJwtException if expired.
        // We catch it in the filter, but this is a secondary safe-guard.
        return !extractAllClaims(token).getExpiration().before(new Date());
    }

    public long getExpirySeconds() {
        return expiryHours * 3600;
    }
}