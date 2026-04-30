package com.vamshi.urlshortener.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateUrlRequest(
    @NotBlank(message = "longUrl is required")
    @Size(max = 2048, message = "longUrl cannot exceed 2048 characters")
    String longUrl,

    @Pattern(regexp = "^[a-zA-Z0-9_-]{4,10}$", message = "customAlias must be 4-10 chars, only letters, numbers, hyphens, and underscores")
    String customAlias,

    @Future(message = "expiresAt must be in the future")
    OffsetDateTime expiresAt
) {}