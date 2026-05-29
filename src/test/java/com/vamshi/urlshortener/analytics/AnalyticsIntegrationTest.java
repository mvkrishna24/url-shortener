package com.vamshi.urlshortener.analytics;

import com.vamshi.urlshortener.AbstractIntegrationTest;
import com.vamshi.urlshortener.analytics.dto.ClickEvent;
import com.vamshi.urlshortener.entity.Url;
import com.vamshi.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StopWatch;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the async click analytics pipeline.
 *
 * Verifies that:
 * 1. publish() returns immediately (non-blocking).
 * 2. ClickEventConsumer persists the event within the scheduler interval.
 *
 * Extends AbstractIntegrationTest for shared Testcontainers containers.
 * Docker is required; the test is skipped automatically when Docker is absent.
 */
class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ClickEventPublisher publisher;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UrlRepository urlRepository;

    private static final long SEED_URL_ID = 990_001L;
    private static final String SEED_SHORT_CODE = "anl001";

    @BeforeEach
    void seedData() {
        jdbcTemplate.update("DELETE FROM clicks WHERE url_id = ?", SEED_URL_ID);
        // merge() handles both first-run (insert) and repeated-run (no-op update) cases
        Url url = Url.builder()
                .id(SEED_URL_ID)
                .shortCode(SEED_SHORT_CODE)
                .longUrl("https://analytics-integration-test.example.com")
                .customAlias(false)
                .build();
        urlRepository.save(url);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM clicks WHERE url_id = ?", SEED_URL_ID);
        urlRepository.deleteById(SEED_URL_ID);
    }

    @Test
    void publish_isNonBlocking_andEventPersistedByConsumer() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        publisher.publish(new ClickEvent(
                SEED_URL_ID,
                OffsetDateTime.now(),
                "8.8.8.8",
                "https://news.ycombinator.com",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

        stopWatch.stop();

        assertThat(stopWatch.getTotalTimeMillis())
                .as("publish() only enqueues — must return in under 50ms")
                .isLessThan(50);

        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(250, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM clicks WHERE url_id = ?",
                            Integer.class, SEED_URL_ID);
                    assertThat(count).isGreaterThanOrEqualTo(1);
                });
    }
}
