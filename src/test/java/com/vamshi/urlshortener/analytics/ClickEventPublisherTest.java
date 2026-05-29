package com.vamshi.urlshortener.analytics;

import com.vamshi.urlshortener.analytics.dto.ClickEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ClickEventPublisherTest {

    private ClickEventPublisher publisher;
    private MeterRegistry meterRegistry;
    private Counter droppedCounter;

    @BeforeEach
    void setUp() {
        meterRegistry = mock(MeterRegistry.class);
        droppedCounter = mock(Counter.class);
        when(meterRegistry.counter(anyString())).thenReturn(droppedCounter);
        publisher = new ClickEventPublisher(meterRegistry);
    }

    @Test
    void publish_IncrementsQueueSize() {
        ClickEvent event = new ClickEvent(1L, OffsetDateTime.now(), "127.0.0.1", "https://google.com", "Mozilla/5.0");
        publisher.publish(event);

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        verify(droppedCounter, never()).increment();
    }

    @Test
    void publish_FullQueue_DropsEventsAndIncrementsMetric() {
        // Fill the queue to its capacity of 10000
        for (int i = 0; i < 10000; i++) {
            publisher.publish(new ClickEvent(1L, OffsetDateTime.now(), "127.0.0.1", null, null));
        }
        assertThat(publisher.getQueueSize()).isEqualTo(10000);

        // Publish one more - should be dropped safely
        publisher.publish(new ClickEvent(1L, OffsetDateTime.now(), "127.0.0.1", null, null));

        assertThat(publisher.getQueueSize()).isEqualTo(10000);
        verify(meterRegistry, times(1)).counter("urlshortener.clicks.dropped");
        verify(droppedCounter, times(1)).increment();
    }
}