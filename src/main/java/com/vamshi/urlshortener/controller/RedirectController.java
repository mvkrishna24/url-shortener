package com.vamshi.urlshortener.controller;

import com.vamshi.urlshortener.analytics.ClickEventPublisher;
import com.vamshi.urlshortener.analytics.dto.ClickEvent;
import com.vamshi.urlshortener.exception.Exceptions.*;
import com.vamshi.urlshortener.service.ResolvedUrl;
import com.vamshi.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

@Controller
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final UrlService urlService;
    private final ClickEventPublisher clickEventPublisher;

    public RedirectController(UrlService urlService, ClickEventPublisher clickEventPublisher) {
        this.urlService = urlService;
        this.clickEventPublisher = clickEventPublisher;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", UUID.randomUUID().toString())) {
            ResolvedUrl resolved = urlService.resolveShortCode(shortCode);
            publishClickEvent(resolved.urlId(), request);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(resolved.longUrl())).build();
        } catch (ShortCodeNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML)
                    .body("<html><body><h1>404 Not Found</h1><p>The link you are looking for does not exist.</p></body></html>");
        } catch (ShortCodeExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.TEXT_HTML)
                    .body("<html><body><h1>410 Gone</h1><p>This link has expired.</p></body></html>");
        }
    }

    private void publishClickEvent(Long urlId, HttpServletRequest request) {
        try {
            String ip = resolveClientIp(request);
            String referrer = request.getHeader("Referer");
            String userAgent = request.getHeader("User-Agent");
            clickEventPublisher.publish(new ClickEvent(urlId, OffsetDateTime.now(), ip, referrer, userAgent));
        } catch (Exception e) {
            // Analytics failure must never affect the redirect. Log and continue.
            log.warn("Failed to publish click event for urlId={}: {}", urlId, e.getMessage());
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated list; the first entry is the client IP.
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
