package com.vamshi.urlshortener.analytics;

import com.vamshi.urlshortener.analytics.dto.AnalyticsResponse;
import com.vamshi.urlshortener.entity.Url;
import com.vamshi.urlshortener.url.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class AnalyticsController {

    private final UrlRepository urlRepository;
    private final AnalyticsService analyticsService;

    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "7") int days,
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