/**
 * Business logic layer — the heart of the application.
 *
 * Rules:
 * - All service classes are interfaces with a single Impl (enables easy mocking in tests).
 * - Owns transaction boundaries (@Transactional lives here, NOT in controllers/repos).
 * - Calls repositories for persistence and Redis for cache read-through.
 * - Fires async events (click tracking) via @Async methods or ApplicationEventPublisher.
 */
package com.vamshi.urlshortener.service;
