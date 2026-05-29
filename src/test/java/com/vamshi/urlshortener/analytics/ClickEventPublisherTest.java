package com.vamshi.urlshortener.analytics;

import com.vamshi.urlshortener.analytics.dto.ClickEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ClickEventPublisherTest {

    private ClickEventPublisher publisher;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new ClickEventPublisher(meterRegistry);
    }

    @Test
    void publish_acceptedEvent_incrementsQueueSize() {
        ClickEvent event = new ClickEvent(1L, OffsetDateTime.now(), "127.0.0.1", "https://google.com", "Mozilla/5.0");
        publisher.publish(event);

        assertThat(publisher.getQueueSize()).isEqualTo(1);
        assertThat(meterRegistry.counter("urlshortener.clicks.dropped").count()).isZero();
    }

    @Test
    void publish_fullQueue_dropsEventAndIncrementsDropCounter() {
        for (int i = 0; i < ClickEventPublisher.QUEUE_CAPACITY; i++) {
            publisher.publish(new ClickEvent(1L, OffsetDateTime.now(), "127.0.0.1", null, null));
        }
        assertThat(publisher.getQueueSize()).isEqualTo(ClickEventPublisher.QUEUE_CAPACITY);

        publisher.publish(new ClickEvent(1L, OffsetDateTime.now(), "127.0.0.1", null, null));

        assertThat(publisher.getQueueSize()).isEqualTo(ClickEventPublisher.QUEUE_CAPACITY);
        assertThat(meterRegistry.counter("urlshortener.clicks.dropped").count()).isEqualTo(1.0);
    }

    @Test
    void drain_removesRequestedEventsFromQueue() {
        publisher.publish(new ClickEvent(1L, OffsetDateTime.now(), "1.1.1.1", null, null));
        publisher.publish(new ClickEvent(2L, OffsetDateTime.now(), "2.2.2.2", null, null));
        publisher.publish(new ClickEvent(3L, OffsetDateTime.now(), "3.3.3.3", null, null));

        assertThat(publisher.drain(2)).hasSize(2);
        assertThat(publisher.getQueueSize()).isEqualTo(1);
    }

    @Test
    void publish_doesNotThrow_forNullFields() {
        ClickEvent event = new ClickEvent(1L, OffsetDateTime.now(), null, null, null);
        assertThat(publisher.getQueueSize()).isZero();
        publisher.publish(event);
        assertThat(publisher.getQueueSize()).isEqualTo(1);
    }
}
