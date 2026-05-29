package com.vamshi.urlshortener.controller;

import com.vamshi.urlshortener.exception.Exceptions.*;
import com.vamshi.urlshortener.service.ResolvedUrl;
import com.vamshi.urlshortener.service.UrlService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;
import java.util.UUID;

@Controller
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) { this.urlService = urlService; }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", UUID.randomUUID().toString())) {
            ResolvedUrl resolved = urlService.resolveShortCode(shortCode);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(resolved.longUrl())).build();
        } catch (ShortCodeNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML)
                    .body("<html><body><h1>404 Not Found</h1><p>The link you are looking for does not exist.</p></body></html>");
        } catch (ShortCodeExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.TEXT_HTML)
                    .body("<html><body><h1>410 Gone</h1><p>This link has expired.</p></body></html>");
        }
    }
}