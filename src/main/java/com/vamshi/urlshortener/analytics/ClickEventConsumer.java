package com.vamshi.urlshortener.analytics;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.vamshi.urlshortener.analytics.dto.ClickEvent;
import lombok.extern.slf4j.Slf4j;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
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
    private DatabaseReader geoIpReader;

    public ClickEventConsumer(ClickEventPublisher publisher, JdbcTemplate jdbcTemplate) {
        this.publisher = publisher;
        this.jdbcTemplate = jdbcTemplate;
        
        this.uaa = UserAgentAnalyzer.newBuilder()
                .hideMatcherLoadStats()
                .withCache(10000)
                .build();

        try {
            // Ensure GeoLite2-Country.mmdb is in the project root or configure path.
            File database = new File("GeoLite2-Country.mmdb");
            if (database.exists()) {
                this.geoIpReader = new DatabaseReader.Builder(database).build();
            } else {
                log.warn("GeoLite2-Country.mmdb not found. Country resolution will be disabled.");
            }
        } catch (IOException e) {
            log.error("Failed to initialize GeoIP Reader", e);
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void processClickEvents() {
        List<ClickEvent> batch = publisher.drain(500);
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
                
                String countryCode = null;
                if (geoIpReader != null && event.ipAddress() != null) {
                    try {
                        InetAddress ip = InetAddress.getByName(event.ipAddress());
                        countryCode = geoIpReader.country(ip).getCountry().getIsoCode();
                    } catch (IOException | GeoIp2Exception ignored) {
                        // Silently ignore parsing errors per-IP to prevent batch failure
                    }
                }
                ps.setString(5, countryCode);

                String deviceType = "unknown";
                if (event.userAgent() != null) {
                    UserAgent parsedAgent = uaa.parse(event.userAgent());
                    deviceType = parsedAgent.getValue(UserAgent.DEVICE_CLASS); // mobile, desktop, tablet, robot
                }
                ps.setString(6, deviceType);
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });

        long elapsed = System.currentTimeMillis() - start;
        log.debug("Batch inserted {} click events in {}ms (queue remaining: {})",
                batch.size(), elapsed, publisher.getQueueSize());
    }
}