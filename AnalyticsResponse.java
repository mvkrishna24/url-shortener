package com.vamshi.urlshortener.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AnalyticsResponse {
    private long totalClicks;
    
    // Ordered list representing daily counts
    private List<Map<String, Object>> dailyClicks;
    private List<Map<String, Object>> topCountries;
    private List<Map<String, Object>> topReferrers;
    private Map<String, Double> deviceBreakdown;
}