# Performance Validation

This document explains how to validate URL shortener performance locally with k6.
It defines the workflow and target thresholds; it does not record benchmark
results. Add numbers only after running the tests on a known machine and setup.

## Goals

- Redirects should stay fast because they are the hottest path.
- Warm-cache redirects should meet:
  - p95 < 50 ms locally
  - p99 < 100 ms locally
- Unexpected error rate should stay below 1%.
- Rate-limit abuse tests should produce HTTP 429 responses.
- Analytics publishing must never break or materially slow redirects.

## Local Setup

Required tools and services:

- Java 17
- Maven
- Docker Desktop
- k6
- PostgreSQL and Redis from `docker-compose.yml`
- Spring Boot app running locally

Start dependencies:

```bash
docker compose up -d postgres redis
```

Start the app in another terminal:

```bash
mvn spring-boot:run
```

Default base URL:

```bash
export BASE_URL=http://localhost:8080
```

## Environment Variables

- `BASE_URL`: application base URL. Defaults to `http://localhost:8080`.
- `AUTH_TOKEN`: JWT for authenticated scenarios.
- `SHORT_CODE`: existing short code for redirect scenarios.
- `LONG_URL`: base destination URL used when scripts create test URLs.

Optional tuning variables used by scripts:

- `RATE`: request arrival rate per second.
- `DURATION`: scenario duration, for example `1m`.
- `VUS`: pre-allocated virtual users.
- `MAX_VUS`: maximum virtual users for arrival-rate scenarios.
- `ITERATIONS`: total requests for the rate-limit abuse script.
- `CLIENT_IP`: client IP used by the rate-limit abuse script.
- `WARMUP_REQUESTS`: cache warm-up redirect count before measured traffic.

## Authentication Setup

Create a user and capture a token:

```bash
curl -s -X POST "$BASE_URL/api/v1/auth/signup" \
  -H 'Content-Type: application/json' \
  -d '{"email":"perf@example.com","password":"password1"}'

export AUTH_TOKEN=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"perf@example.com","password":"password1"}' | jq -r '.token')
```

## Create A Reusable Short Code

```bash
export SHORT_CODE=$(curl -s -X POST "$BASE_URL/api/v1/urls" \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/performance-target"}' | jq -r '.shortCode')
```

## Test Commands

Anonymous URL shortening:

```bash
k6 run load-tests/anonymous-shorten.js
```

Authenticated URL shortening:

```bash
AUTH_TOKEN="$AUTH_TOKEN" k6 run load-tests/authenticated-shorten.js
```

Redirect-heavy workload against an existing short code:

```bash
SHORT_CODE="$SHORT_CODE" k6 run load-tests/redirect-heavy.js
```

Warm-cache redirect workload:

```bash
k6 run load-tests/warm-cache-redirect.js
```

Rate-limit abuse validation:

```bash
k6 run load-tests/rate-limit.js
```

Analytics side-effect workload:

```bash
SHORT_CODE="$SHORT_CODE" k6 run load-tests/analytics-side-effect.js
```

## Why Redis Improves Redirects

Redirects first check Redis for `shortCode -> longUrl`. On a cache hit, the app
avoids a PostgreSQL lookup and returns the 302 response with less work on the
critical path. The warm-cache script creates or reuses a short code, performs
warm-up redirects, and then validates redirect latency after the cache is hot.

## Why Async Analytics Protects Redirect Latency

Redirects publish click events to an in-memory queue and return the 302 response.
GeoIP lookup, user-agent parsing, and PostgreSQL batch inserts happen later in a
scheduled consumer. The analytics side-effect script sends redirect traffic with
headers that exercise analytics enrichment inputs while asserting redirect
latency and correctness.

## Interpreting k6 Output

Key fields:

- `http_req_duration`: end-to-end request duration observed by k6.
- `http_req_failed`: requests k6 considered failed. The rate-limit abuse script
  treats 429 as expected.
- `checks`: application-level assertions such as status code and headers.
- Custom counters such as `urls_created` and `requests_rejected_429`.

If thresholds fail, inspect:

- Whether the app is running with the expected profile.
- Whether PostgreSQL and Redis are healthy.
- Whether the tested URL was warmed into Redis.
- Whether local machine CPU or Docker resource limits are saturated.
- Whether 429s are expected for the scenario being run.

## Recording Results

Do not add benchmark numbers to this repository until the tests have actually
been run. When recording results, include:

- Date and machine details.
- Git commit SHA.
- Java, Maven, Docker, PostgreSQL, Redis, and k6 versions.
- Exact command and environment variables.
- p95, p99, throughput, error rate, and relevant custom counters.
