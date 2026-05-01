package com.vamshi.urlshortener.analytics;

import com.vamshi.urlshortener.analytics.dto.AnalyticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsResponse getAnalytics(Long urlId, int days) {
        
        String totalSql = "SELECT COUNT(*) FROM clicks WHERE url_id = ?";
        Long totalClicks = jdbcTemplate.queryForObject(totalSql, Long.class, urlId);

        // Daily clicks using date_trunc. Cast to interval via multiplication — JDBC cannot
        // parameterize inside a string literal ('? days' stays a literal, not a bind variable).
        String dailySql = "SELECT date_trunc('day', clicked_at) as click_date, COUNT(*) as count " +
                          "FROM clicks WHERE url_id = ? AND clicked_at >= NOW() - (? * INTERVAL '1 day') " +
                          "GROUP BY click_date ORDER BY click_date ASC";
        List<Map<String, Object>> dailyClicks = jdbcTemplate.queryForList(dailySql, urlId, days);

        String topCountriesSql = "SELECT country_code, COUNT(*) as count FROM clicks " +
                                 "WHERE url_id = ? GROUP BY country_code ORDER BY count DESC LIMIT 5";
        List<Map<String, Object>> topCountries = jdbcTemplate.queryForList(topCountriesSql, urlId);

        String topReferrersSql = "SELECT COALESCE(referrer, 'Direct') as referrer, COUNT(*) as count FROM clicks " +
                                 "WHERE url_id = ? GROUP BY referrer ORDER BY count DESC LIMIT 5";
        List<Map<String, Object>> topReferrers = jdbcTemplate.queryForList(topReferrersSql, urlId);

        String deviceSql = "SELECT device_type, COUNT(*) as count FROM clicks WHERE url_id = ? GROUP BY device_type";
        List<Map<String, Object>> deviceData = jdbcTemplate.queryForList(deviceSql, urlId);
        
        Map<String, Double> deviceBreakdown = new HashMap<>();
        if (totalClicks != null && totalClicks > 0) {
            deviceData.forEach(row -> {
                String device = (String) row.get("device_type");
                Long count = ((Number) row.get("count")).longValue();
                deviceBreakdown.put(device, (count * 100.0) / totalClicks);
            });
        }

        return AnalyticsResponse.builder().totalClicks(totalClicks != null ? totalClicks : 0)
                .dailyClicks(dailyClicks).topCountries(topCountries).topReferrers(topReferrers).deviceBreakdown(deviceBreakdown).build();
    }
}