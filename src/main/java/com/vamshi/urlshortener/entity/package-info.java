/**
 * JPA entities — direct mapping of database tables.
 *
 * Rules:
 * - Annotate with @Entity + @Table(name = "...") using snake_case table names.
 * - Use @Column(nullable = false) to mirror DB constraints at the ORM level.
 * - Prefer @CreationTimestamp / @UpdateTimestamp over manual lifecycle hooks.
 * - Avoid bidirectional associations unless you truly need them — they cause
 *   serialisation issues and surprise N+1 queries.
 * - Never expose entities directly through the API; map to DTOs first.
 */
package com.vamshi.urlshortener.entity;
