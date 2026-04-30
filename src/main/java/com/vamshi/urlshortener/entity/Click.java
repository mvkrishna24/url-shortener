package com.vamshi.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * An immutable click event. Written asynchronously (fire-and-forget) by the
 * redirect service so that the HTTP 302 response is not blocked on a DB write.
 *
 * ip_address is stored as TEXT (not PostgreSQL INET) to avoid the Hibernate/JDBC
 * type-mapping issue: Hibernate maps Java String → VARCHAR, but PostgreSQL rejects
 * VARCHAR for an INET column. Since this field is stored/retrieved but never queried
 * with subnet operators, TEXT is the pragmatic choice. See V3 migration for details.
 */
@Entity
@Table(
        name = "clicks",
        indexes = @Index(
                name = "idx_clicks_url_id_clicked_at",
                columnList = "url_id, clicked_at"
                // NOTE: JPA @Index cannot express sort direction. The actual index
                // is created with `clicked_at DESC` by the V3 Flyway migration,
                // eliminating the sort step in ORDER BY clicked_at DESC queries.
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ON DELETE CASCADE in DB: deleting a URL removes all its click history.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private Url url;

    @CreationTimestamp
    @Column(name = "clicked_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime clickedAt;

    // ISO 3166-1 alpha-2 code resolved from ip_address via geo-IP lookup
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(columnDefinition = "TEXT")
    private String referrer;

    // 'mobile' | 'desktop' | 'tablet' | 'bot'
    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(name = "ip_address")
    private String ipAddress;
}
