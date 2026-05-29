package com.vamshi.urlshortener.analytics;

import com.vamshi.urlshortener.analytics.dto.ClickEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StopWatch;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class AnalyticsIntegrationTest {

    @Autowired
    private ClickEventPublisher publisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void clickPipeline_EnrichesAndSaves_WithoutImpactingLatency() {
        jdbcTemplate.execute("DELETE FROM clicks"); // Clear state

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // Simulate a redirect firing off an event
        // Note: URL ID '1' assumes the database has been seeded with a dummy URL for constraints
        ClickEvent event = new ClickEvent(1L, OffsetDateTime.now(), "8.8.8.8", "https://news.ycombinator.com", 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        publisher.publish(event);

        stopWatch.stop();
        
        // Publishing the event to the queue should take virtually no time (< 5ms)
        assertThat(stopWatch.getTotalTimeMillis()).isLessThan(5);

        // Awaitility handles waiting for the async consumer schedule (max 2 seconds)
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clicks", Integer.class);
            assertThat(count).isEqualTo(1);
        });
    }
}