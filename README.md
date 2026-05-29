# Distributed URL Shortener

Production-grade URL shortener built with Java 17, Spring Boot, PostgreSQL, Redis, JWT authentication, Flyway migrations, async click analytics, and k6 load-test scenarios.

This project focuses on backend system design: short URL generation, low-latency redirects, cache-aware reads, rate limiting, resilient analytics collection, and deployment-ready operational practices.

## Problem Solved

URL shorteners are read-heavy systems. A production design has to make redirects fast while still handling authentication, custom aliases, abuse protection, observability, and analytics.

This project separates the critical redirect path from non-critical work:

- Redis serves hot redirects without repeated database reads.
- PostgreSQL remains the source of truth for URLs, users, and click data.
- Analytics events are published asynchronously so analytics failures do not break redirects.
- Redis-backed rate limiting protects API endpoints from abusive clients.

## Core Features

- Create short URLs from long HTTP/HTTPS URLs.
- Optional custom aliases with uniqueness enforcement.
- Anonymous URL creation is supported.
- Authenticated URL creation associates URLs with the current user.
- JWT signup, login, and current-user endpoints.
- User dashboard endpoint for a user's own URLs.
- Redirect endpoint with read-through Redis caching.
- Sliding-window rate limiter backed by Redis Lua scripting.
- Async click analytics pipeline with bounded in-memory queue and JDBC batch inserts.
- Analytics endpoint for URL owners.
- Flyway-managed PostgreSQL schema.
- Actuator, Micrometer, Prometheus metrics, and structured logging.
- Docker Compose for local PostgreSQL and Redis.
- Render deployment blueprint.
- k6 load-test scripts for performance validation.

## System Design Highlights

```text
Client
  |
  | POST /api/v1/urls
  v
Spring Boot API -----> PostgreSQL
  |
  | GET /{shortCode}
  v
Redis cache ----miss----> PostgreSQL
  |
  v
302 redirect
  |
  +---- async ClickEvent queue ----> analytics worker ----> clicks table
```

- Base62 encoding keeps short codes URL-safe and compact.
- PostgreSQL sequence blocks reduce ID-generation round trips.
- Redis read-through caching optimizes hot redirects.
- Redis sorted sets and Lua make rate limiting atomic.
- JWT keeps API authentication stateless.
- The click analytics pipeline is intentionally decoupled from redirect response latency.

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3 |
| Database | PostgreSQL |
| Cache | Redis |
| Migrations | Flyway |
| Auth | JWT, Spring Security, BCrypt |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers |
| Observability | Actuator, Micrometer, Prometheus |
| API Docs | springdoc OpenAPI |
| Load Testing | k6 |
| Deployment | Docker, Docker Compose, Render |

## Local Setup

Prerequisites:

- Java 17
- Maven 3.x
- Docker Desktop
- PostgreSQL and Redis via Docker Compose

Start dependencies:

```bash
docker compose up -d postgres redis
```

Run the app:

```bash
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

## Docker Setup

Start local services:

```bash
docker compose up -d
```

Stop services:

```bash
docker compose down
```

Reset local PostgreSQL and Redis volumes:

```bash
docker compose down -v
docker compose up -d postgres redis
```

## Environment Variables

Use [.env.example](.env.example) as the safe local template.

Common app configuration:

| Variable | Purpose | Local default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `dev` |
| `SERVER_PORT` | HTTP port | `8080` |
| `APP_BASE_URL` | Base URL returned in short URL responses | `http://localhost:8080` |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database | `urlshortener` |
| `DB_USER` | PostgreSQL user | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | empty |
| `REDIS_URL` | Production Redis connection URL | profile-specific |
| `JWT_SECRET` | JWT signing secret | dev-only fallback |
| `JWT_EXPIRATION` | JWT expiration in hours | `1` |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origins | `http://localhost:3000` |

Production uses `REDIS_URL` in `application-prod.yml` for managed Redis providers such as Upstash.

GeoIP country enrichment is optional. If `GeoLite2-Country.mmdb` is not present, click events are still stored, but country resolution is disabled.

## API Overview

Base API URL:

```text
http://localhost:8080/api/v1
```

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/signup` | Public | Create a user |
| `POST` | `/auth/login` | Public | Return JWT |
| `GET` | `/auth/me` | JWT | Current user |
| `POST` | `/urls` | Optional JWT | Create short URL |
| `GET` | `/users/me/urls` | JWT | List current user's URLs |
| `GET` | `/urls/{shortCode}/analytics?days=7` | JWT owner only | URL analytics |
| `GET` | `/{shortCode}` | Public | Redirect to long URL |

See [docs/api.md](docs/api.md) for request and response examples.

## Testing

Compile:

```bash
mvn clean compile
```

Run tests:

```bash
mvn test
```

Integration tests use Testcontainers and may be skipped locally when Docker or the Docker socket is unavailable. CI environments with Docker available should run them normally.

## Load Testing

k6 scripts live in [load-tests/](load-tests/).

Performance validation workflow:

- [docs/performance.md](docs/performance.md)

The repository defines performance targets and commands, but benchmark numbers should only be added after actually running tests on a known machine and configuration.

## Deployment

Deployment documentation:

- [DEPLOYMENT.md](DEPLOYMENT.md)

Render blueprint:

- [render.yaml](render.yaml)

## Project Structure

```text
src/main/java/com/vamshi/urlshortener
  analytics/      async click analytics and analytics API
  auth/           signup, login, auth DTOs
  config/         Spring infrastructure config
  controller/     URL creation and redirect controllers
  dto/            public URL DTOs
  entity/         JPA entities
  exception/      API exception handling
  ratelimit/      Redis sliding-window rate limiter
  repository/     Spring Data repositories
  security/       JWT filter, JWT service, security config
  service/        URL, ID generation, cache services
  user/           user-facing endpoints and DTOs

src/main/resources
  db/migration/   Flyway SQL migrations
  scripts/        Redis Lua scripts

docs/             architecture, API, operations, performance notes
load-tests/       k6 load-test scenarios
```

## Current Limitations

- No refresh-token flow yet; users log in again after JWT expiry.
- In-memory analytics queue can lose buffered click events if the process exits.
- GeoIP country enrichment requires a local MaxMind database file.
- Redirect endpoint is intentionally not rate-limited.
- Load-test scripts are provided, but this README does not claim measured benchmark results.
- Render free-tier deployments can cold start after inactivity.

## Future Improvements

- Move analytics events to Kafka, RabbitMQ, or another durable queue.
- Add refresh tokens and token revocation.
- Add account deletion and URL deletion workflows.
- Add spam and malware checks for destination URLs.
- Add richer dashboard aggregation and pagination.
- Add production dashboards and alerting around Redis, PostgreSQL, queue depth, and HTTP latency.
