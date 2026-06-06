package com.vamshi.urlshortener.service;

import com.vamshi.urlshortener.repository.UrlRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
public class UrlCleanupService {

    private final UrlRepository urlRepository;
    private final Counter cleanupCounter;

    public UrlCleanupService(UrlRepository urlRepository, MeterRegistry meterRegistry) {
        this.urlRepository = urlRepository;
        this.cleanupCounter = Counter.builder("urls.cleanup.deleted")
                .description("Number of expired URLs removed by the scheduled cleanup job")
                .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredUrls() {
        int deleted = urlRepository.deleteExpiredUrls(OffsetDateTime.now());
        cleanupCounter.increment(deleted);
        log.info("Expired URL cleanup: deleted={} urls", deleted);
    }
}
