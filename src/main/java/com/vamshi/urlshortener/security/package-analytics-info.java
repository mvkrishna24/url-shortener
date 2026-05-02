/**
 * Async click-tracking pipeline.
 *
 * <p>Clicks are published to a bounded {@link java.util.concurrent.LinkedBlockingQueue} by the
 * redirect controller and drained every second by {@code ClickEventConsumer}, which enriches each
 * event with GeoIP country resolution and User-Agent device classification before writing a JDBC
 * batch insert to PostgreSQL. The pipeline is intentionally lossy under back-pressure: if the
 * queue is full, events are dropped rather than blocking the HTTP thread.
 */
package com.vamshi.urlshortener.analytics;
