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
    public ResponseEntity<UrlResponse> shortenUrl(@Valid @RequestBody CreateUrlRequest request) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", UUID.randomUUID().toString())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(urlService.shortenUrl(request));
        }
    }
}