package com.vamshi.urlshortener.repository;

import com.vamshi.urlshortener.entity.Click;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface ClickRepository extends JpaRepository<Click, Long> {

    // Total clicks for a URL — used in dashboard summary cards.
    // "UrlId" traverses click.url.id → WHERE url_id = ?  (no JOIN needed)
    long countByUrlId(Long urlId);

    // Recent clicks for timeline charts; the composite index on (url_id, clicked_at DESC)
    // makes this a straight index scan with no sort step.
    List<Click> findTop100ByUrlIdOrderByClickedAtDesc(Long urlId);

    // Breakdown by country for the geo chart. Native query returns raw tuples
    // for performance — we don't need full Click objects for aggregates.
    @Query(value = """
            SELECT country_code, COUNT(*) AS total
            FROM clicks
            WHERE url_id = :urlId AND country_code IS NOT NULL
            GROUP BY country_code
            ORDER BY total DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Map<String, Object>> countByCountryForUrl(@Param("urlId") Long urlId);

    // Breakdown by device type.
    @Query(value = """
            SELECT device_type, COUNT(*) AS total
            FROM clicks
            WHERE url_id = :urlId AND device_type IS NOT NULL
            GROUP BY device_type
            """, nativeQuery = true)
    List<Map<String, Object>> countByDeviceTypeForUrl(@Param("urlId") Long urlId);
}
