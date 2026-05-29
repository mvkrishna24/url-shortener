package com.vamshi.urlshortener.analytics;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.vamshi.urlshortener.analytics.dto.ClickEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Slf4j
@Component
public class ClickEventConsumer {

    private final ClickEventPublisher publisher;
    private final JdbcTemplate jdbcTemplate;
    private final UserAgentAnalyzer uaa;
    private final Counter processedCounter;
    private final int batchSize;
    private final long slowQueryThresholdMs;
    private DatabaseReader geoIpReader;

    public ClickEventConsumer(
            ClickEventPublisher publisher,
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.analytics.consumer.batch-size:500}") int batchSize,
            @Value("${app.analytics.slow-query-threshold-ms:500}") long slowQueryThresholdMs) {

        this.publisher = publisher;
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
        this.processedCounter = Counter.builder("urlshortener.clicks.processed")
                .description("Number of click events successfully written to the database")
                .register(meterRegistry);

        this.uaa = UserAgentAnalyzer.newBuilder()
                .hideMatcherLoadStats()
                .withCache(10_000)
                .build();

        try {
            File database = new File("GeoLite2-Country.mmdb");
            if (database.exists()) {
                this.geoIpReader = new DatabaseReader.Builder(database).build();
            } else {
                log.warn("GeoLite2-Country.mmdb not found — country resolution disabled");
            }
        } catch (IOException e) {
            log.error("Failed to initialize GeoIP reader", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.analytics.consumer.interval-ms:1000}")
    public void processClickEvents() {
        List<ClickEvent> batch = publisher.drain(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        long start = System.currentTimeMillis();
        String sql = "INSERT INTO clicks (url_id, clicked_at, ip_address, referrer, country_code, device_type) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ClickEvent event = batch.get(i);
                ps.setLong(1, event.urlId());
                ps.setTimestamp(2, Timestamp.from(event.clickedAt().toInstant()));
                ps.setString(3, event.ipAddress());
                ps.setString(4, event.referrer());
                ps.setString(5, resolveCountry(event.ipAddress()));
                ps.setString(6, resolveDeviceType(event.userAgent()));
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });

        processedCounter.increment(batch.size());
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > slowQueryThresholdMs) {
            log.warn("Slow analytics batch: {} events in {}ms (threshold {}ms)",
                    batch.size(), elapsed, slowQueryThresholdMs);
        } else {
            log.debug("Inserted {} click events in {}ms (queue remaining: {})",
                    batch.size(), elapsed, publisher.getQueueSize());
        }
    }

    private String resolveCountry(String ipAddress) {
        if (geoIpReader == null || ipAddress == null || ipAddress.isBlank()) return null;
        try {
            return geoIpReader.country(InetAddress.getByName(ipAddress)).getCountry().getIsoCode();
        } catch (IOException | GeoIp2Exception e) {
            return null;
        }
    }

    private String resolveDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "unknown";
        try {
            return uaa.parse(userAgent).getValue(UserAgent.DEVICE_CLASS);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
