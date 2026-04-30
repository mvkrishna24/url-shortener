package com.vamshi.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * A shortened URL mapping. The numeric {@code id} is Base62-encoded to produce
 * the {@code shortCode}. Anonymous creation is allowed — {@code user} may be null.
 */
@Entity
@Table(
        name = "urls",
        uniqueConstraints = @UniqueConstraint(name = "uq_urls_short_code", columnNames = "short_code"),
        indexes = @Index(name = "idx_urls_user_id", columnList = "user_id")
        // The partial index on expires_at (WHERE expires_at IS NOT NULL) cannot be expressed
        // via @Index — it is created by the V2 Flyway migration.
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Url {

    @Id
    private Long id;

    @Column(name = "short_code", nullable = false, length = 10)
    private String shortCode;

    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    // LAZY fetch: we almost never need the full User when resolving a short code.
    // The DB stores user_id (FK). Spring Data can filter by user.id without loading User.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")   // nullable — anonymous URLs allowed
    private User user;

    // @Builder.Default is required: without it, @Builder ignores the field initializer
    // and the boolean defaults to false anyway — but it makes the intent explicit.
    @Builder.Default
    @Column(name = "custom_alias", nullable = false)
    private boolean customAlias = false;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;
}
