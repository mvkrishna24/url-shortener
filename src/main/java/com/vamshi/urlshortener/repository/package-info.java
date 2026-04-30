/**
 * Data access layer — Spring Data JPA repositories + custom query methods.
 *
 * Rules:
 * - Extend JpaRepository<Entity, ID> for standard CRUD.
 * - Use @Query with JPQL (or native SQL) for complex queries.
 * - Never call repositories directly from controllers — always go through a service.
 * - Redis access lives in the service layer (or a dedicated CacheService), not here.
 */
package com.vamshi.urlshortener.repository;
