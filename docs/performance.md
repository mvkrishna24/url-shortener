# Performance Benchmarks

## Test Environment

- Machine: MacBook Air M5 (Apple Silicon, 10-core CPU, 16 GB RAM)
- Project target: Java 17
- Benchmark JVM: Homebrew OpenJDK 26.0.1 (Maven runtime)
- Database: PostgreSQL 16.14 in Docker (single container)
- Cache: Redis 7.4.9 in Docker (single container)
- Load generator: k6 v2.0.0
- Test date: 2026-06-07
- Git commit: `dc65ac2`

## Results Summary

| Scenario | RPS | p50 | p95 | p99 | Error Rate |
|----------|-----|-----|-----|-----|------------|
| Anonymous URL shortening | 19.98 | 8.16 ms | 11.46 ms | 12.84 ms | 0.00% |
| Redirect (warm Redis cache) | 100.26 | 2.65 ms | 3.54 ms | 4.13 ms | 0.00% |
| Authenticated URL shortening | 10.00 | 9.37 ms | 11.35 ms | 12.39 ms | 0.00% |
| Rate limiter correctness | N/A | N/A | N/A | N/A | 100.00% checks passed |

These are validated throughput points, not saturation limits. The arrival-rate
scripts intentionally held traffic at 20 anonymous writes/sec, 100 redirects/sec,
and 10 authenticated writes/sec for one minute.

## Cache Performance

- Redis cache hit rate during the warm-cache scenario: 99.98%
- Observed cache counters: 6,019 hits, 1 miss
- PostgreSQL redirect lookups avoided after warm-up: 6,019 of 6,020
- Cache TTL: 1 hour
- Key pattern: `url:{shortCode}`

## Rate Limiter

- Anonymous limit: 100 req/min per IP
- Authenticated limit: 1,000 req/min per user
- Algorithm: sliding-window log via Redis sorted sets and a Lua script
- Correctness result: requests 1-100 accepted; requests 101-140 rejected with 429
- k6 checks passed: 500/500

## Methodology

The application ran with the `dev` profile against the Docker Compose PostgreSQL
and Redis services. Each throughput scenario used a k6 constant-arrival-rate
executor for one minute:

| Scenario | Configured Arrival Rate | Requests |
|----------|-------------------------|----------|
| Anonymous shortening | 20/sec | 1,200 |
| Warm-cache redirect | 100/sec | 6,021 including setup/warm-up requests |
| Authenticated shortening | 10/sec | 601 |
| Anonymous rate-limit abuse | 140 immediate iterations | 100 accepted, 40 rejected |

The warm-cache script creates one URL, performs 20 warm-up redirects, then sends
6,000 measured redirect iterations. Redirects do not follow the destination.
Percentiles were calculated from the k6 JSON samples; the console summaries and
Prometheus snapshot are retained in `load-tests/results/`.

## Bottleneck Analysis

At the current single-instance setup, the likely bottleneck hierarchy is:

1. PostgreSQL and the 20-connection HikariCP pool on write-heavy workloads.
2. Application CPU for validation, security filters, and request serialization.
3. Redis and network round trips on hot redirect and rate-limit paths.
4. The bounded 10,000-event analytics queue during sustained click bursts.

This benchmark validates latency at fixed arrival rates; it does not establish
the saturation points above. A separate step-load or ramping-arrival-rate test
is required before claiming maximum RPS.

## Scaling Story

To handle 10x the validated load:

- Add stateless app instances behind a load balancer.
- Scale PostgreSQL vertically first, then add read replicas for analytics queries.
- Move Redis to a managed replicated deployment or Redis Cluster as traffic grows.
- Replace the in-memory analytics queue with Kafka or another durable broker.

## Reproduce

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=dev
k6 run --out json=load-tests/results/shorten-anon.json load-tests/anonymous-shorten.js
k6 run --out json=load-tests/results/redirect-warm.json load-tests/warm-cache-redirect.js
AUTH_TOKEN="<jwt>" k6 run --out json=load-tests/results/shorten-auth.json load-tests/authenticated-shorten.js
k6 run load-tests/rate-limit.js
```

`/actuator/prometheus` currently requires a valid bearer token.
