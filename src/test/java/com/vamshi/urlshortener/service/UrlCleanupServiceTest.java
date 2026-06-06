package com.vamshi.urlshortener.service;

import com.vamshi.urlshortener.repository.UrlRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlCleanupServiceTest {

    @Mock private UrlRepository urlRepository;

    private SimpleMeterRegistry meterRegistry;
    private UrlCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cleanupService = new UrlCleanupService(urlRepository, meterRegistry);
    }

    @Test
    void purgeExpiredUrls_deletesExpiredRowsAndIncrementsCounter() {
        when(urlRepository.deleteExpiredUrls(any(OffsetDateTime.class))).thenReturn(5);

        cleanupService.purgeExpiredUrls();

        verify(urlRepository).deleteExpiredUrls(any(OffsetDateTime.class));
        assertThat(meterRegistry.counter("urls.cleanup.deleted").count()).isEqualTo(5.0);
    }

    @Test
    void purgeExpiredUrls_nothingExpired_counterRemainsZero() {
        when(urlRepository.deleteExpiredUrls(any(OffsetDateTime.class))).thenReturn(0);

        cleanupService.purgeExpiredUrls();

        assertThat(meterRegistry.counter("urls.cleanup.deleted").count()).isZero();
    }
}
