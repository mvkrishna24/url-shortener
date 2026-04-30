/**
 * REST controllers — HTTP boundary only.
 *
 * Rules:
 * - No business logic; delegate everything to a service.
 * - Annotate with @RestController + @RequestMapping.
 * - Receive/return DTOs, never entities directly (avoids accidental data leaks).
 * - Keep methods thin: validate input, call service, map to response.
 */
package com.vamshi.urlshortener.controller;
