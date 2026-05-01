package com.vamshi.urlshortener.analytics;

import com.vamshi.urlshortener.analytics.dto.AnalyticsResponse;
import com.vamshi.urlshortener.entity.Url;
import com.vamshi.urlshortener.url.UrlRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Validated
@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class AnalyticsController {

    private static final int MAX_DAYS = 90;

    private final UrlRepository urlRepository;
    private final AnalyticsService analyticsService;

    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "7") @Min(1) @Max(MAX_DAYS) int days,
            @AuthenticationPrincipal Long currentUserId) {

        Optional<Url> urlOpt = urlRepository.findByShortCode(shortCode);
        if (urlOpt.isEmpty()) return ResponseEntity.notFound().build();

        Url url = urlOpt.get();
        if (url.getUser() == null || !url.getUser().getId().equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(analyticsService.getAnalytics(url.getId(), days));
    }
}