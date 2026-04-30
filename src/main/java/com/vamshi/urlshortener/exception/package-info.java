/**
 * Custom exceptions + global error handling.
 *
 * Pattern:
 * - Define typed exceptions (UrlNotFoundException, ShortCodeConflictException, etc.)
 *   that carry enough context to produce a meaningful HTTP response.
 * - A single @RestControllerAdvice class (GlobalExceptionHandler) catches them all
 *   and maps them to a consistent ErrorResponse DTO.
 * - This keeps controller methods free of try/catch noise.
 */
package com.vamshi.urlshortener.exception;
