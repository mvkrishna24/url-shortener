package com.vamshi.urlshortener.analytics.dto;

import java.time.OffsetDateTime;

public record ClickEvent(
        Long urlId,
        OffsetDateTime clickedAt,
        String ipAddress,
        String referrer,
        String userAgent
) {
}