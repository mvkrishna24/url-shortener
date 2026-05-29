package com.vamshi.urlshortener.analytics;

import com.vamshi.urlshortener.analytics.dto.ClickEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
public class ClickEventPublisher {

    static final int QUEUE_CAPACITY = 10_000;

    private final LinkedBlockingQueue<ClickEvent> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final MeterRegistry meterRegistry;

    public ClickEventPublisher(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("urlshortener.clicks.queue.size", queue, LinkedBlockingQueue::size)
                .description("Current number of click events buffered in the in-memory queue")
                .register(meterRegistry);
    }

    public void publish(ClickEvent event) {
        boolean accepted = queue.offer(event);
        if (!accepted) {
            meterRegistry.counter("urlshortener.clicks.dropped").increment();
            log.warn("Click analytics queue is full. Dropped event for URL ID: {}", event.urlId());
        }
    }

    public List<ClickEvent> drain(int maxElements) {
        List<ClickEvent> batch = new ArrayList<>();
        queue.drainTo(batch, maxElements);
        return batch;
    }
    
    public int getQueueSize() { return queue.size(); }
}