package com.vamshi.urlshortener.controller;

import com.vamshi.urlshortener.dto.CreateUrlRequest;
import com.vamshi.urlshortener.dto.UrlResponse;
import com.vamshi.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @Operation(summary = "Create a short URL")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "URL shortened successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or URL"),
        @ApiResponse(responseCode = "409", description = "Custom alias already taken")
    })
    @PostMapping
    public ResponseEntity<UrlResponse> shortenUrl(@Valid @RequestBody CreateUrlRequest request,
                                                  @AuthenticationPrincipal Long currentUserId) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", UUID.randomUUID().toString())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(urlService.shortenUrl(request, currentUserId));
        }
    }

    @Operation(summary = "Delete a short URL")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "URL deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — valid Bearer token required"),
        @ApiResponse(responseCode = "403", description = "Forbidden — you do not own this URL"),
        @ApiResponse(responseCode = "404", description = "Short code not found")
    })
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode,
                                          @AuthenticationPrincipal Long currentUserId) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", UUID.randomUUID().toString())) {
            urlService.deleteUrl(shortCode, currentUserId);
            return ResponseEntity.noContent().build();
        }
    }
}
