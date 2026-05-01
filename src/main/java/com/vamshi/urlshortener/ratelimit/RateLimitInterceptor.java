package com.vamshi.urlshortener.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * MVC interceptor that enforces per-client rate limits on {@code /api/v1/**}.
 *
 * <p><b>Client identification:</b>
 * <ul>
 *   <li>Authenticated principal → identifier = {@code "user:<principalName>"}, higher quota</li>
 *   <li>Anonymous → identifier = {@code "ip:<clientIp>"}, lower quota</li>
 * </ul>
 *
 * <p><b>IP extraction:</b>
 * Reads {@code X-Forwarded-For} first. Behind a reverse proxy (nginx, AWS ALB) the
 * proxy appends the real client IP as the leftmost value:
 * {@code X-Forwarded-For: <client>, <proxy1>, <proxy2>}. We take {@code split(",")[0]}.
 * Note: this value can be spoofed by clients sending a fake header when there is NO
 * trusted proxy in front of the app. In production, the proxy should strip or overwrite
 * this header before forwarding — never trust the raw header from untrusted networks.
 *
 * <p><b>Response headers (set on every request, including 429):</b>
 * <ul>
 *   <li>{@code X-RateLimit-Limit}     — configured cap for this client tier</li>
 *   <li>{@code X-RateLimit-Remaining} — requests left in the current window</li>
 *   <li>{@code X-RateLimit-Reset}     — Unix epoch second when the window resets</li>
 * </ul>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final int unauthRpm;
    private final int authRpm;

    public RateLimitInterceptor(
            RateLimiterService rateLimiterService,
            @Value("${app.rate-limit.unauthenticated-rpm:100}") int unauthRpm,
            @Value("${app.rate-limit.authenticated-rpm:1000}") int authRpm) {
        this.rateLimiterService = rateLimiterService;
        this.unauthRpm = unauthRpm;
        this.authRpm   = authRpm;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        String identifier;
        int    limit;
        if (authenticated) {
            identifier = "user:" + auth.getName();
            limit      = authRpm;
        } else {
            identifier = "ip:" + extractClientIp(request);
            limit      = unauthRpm;
        }

        AcquireResult result = rateLimiterService.tryAcquire(identifier, limit, Duration.ofMinutes(1));

        // X-RateLimit headers must be set on every response — allowed or rejected.
        // Setting them directly on HttpServletResponse here ensures they appear even
        // when the controller's ResponseEntity does not include them.
        response.setHeader("X-RateLimit-Limit",     String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset",     String.valueOf(result.resetEpochSeconds()));

        if (!result.allowed()) {
            // Throw so GlobalExceptionHandler produces a consistent JSON 429 body.
            // Spring MVC catches exceptions from preHandle and routes them through
            // ExceptionHandlerExceptionResolver, so @ControllerAdvice applies normally.
            throw new RateLimitExceededException(result);
        }

        return true;
    }

    /**
     * Extracts the originating client IP, preferring {@code X-Forwarded-For} when present.
     * Always takes the leftmost (client-supplied) value, which is the real client IP when
     * the request flows through a trusted reverse proxy that appends to the header.
     */
    static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
