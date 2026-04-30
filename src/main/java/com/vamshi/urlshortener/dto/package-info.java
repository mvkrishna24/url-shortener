/**
 * Data Transfer Objects — the shapes of data crossing API boundaries.
 *
 * Rules:
 * - Use Java Records for immutable request/response DTOs (Java 16+).
 * - Apply @Valid constraints here, not on entities.
 * - Organise as nested static records or separate files (Request vs Response).
 * - DTOs must never contain JPA annotations or lazy-loaded fields.
 *
 * Convention used here:
 *   CreateUrlRequest, CreateUrlResponse
 *   AuthRequest, AuthResponse
 */
package com.vamshi.urlshortener.dto;
