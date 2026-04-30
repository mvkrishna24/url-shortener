package com.vamshi.urlshortener.repository;

import com.vamshi.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    // Hot path: every redirect call hits this.
    // Spring Data generates: SELECT * FROM urls WHERE short_code = ?
    // Redis cache sits in front of this in the service layer.
    Optional<Url> findByShortCode(String shortCode);

    // Dashboard query: user's link history, newest first.
    // "UserId" is a property path traversal: url.user.id → WHERE user_id = ?
    // Hibernate is smart enough NOT to JOIN the users table; it uses the FK column directly.
    List<Url> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Collision check before inserting a custom alias.
    // Cheaper than findByShortCode: generates SELECT 1 FROM urls WHERE short_code = ? LIMIT 1
    boolean existsByShortCode(String shortCode);

    // Used by the background expiry cleanup job.
    List<Url> findByExpiresAtBeforeAndExpiresAtIsNotNull(OffsetDateTime threshold);
}
