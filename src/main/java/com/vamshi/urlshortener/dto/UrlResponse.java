package com.vamshi.urlshortener.dto;

import java.time.OffsetDateTime;

public record UrlResponse(
    String shortCode,
    String shortUrl,
    String longUrl,
    OffsetDateTime createdAt,
    OffsetDateTime expiresAt
) {}