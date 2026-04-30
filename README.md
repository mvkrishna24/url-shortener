# URL Shortener

Production-grade distributed URL shortener built with Java 17, Spring Boot 3, Redis, and PostgreSQL.
Targets 10K writes/sec with sub-20ms p99 redirect latency.

## Problem Statement

Most URL shorteners are toy CRUD apps. This project is built to demonstrate real system design depth:
scalable counter allocation, multi-layer caching, async analytics, and rate limiting — the kind of
architecture you'd see in a production service handling millions of requests per day.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 |
| Framework | Spring Boot 3.x |
| Primary DB | PostgreSQL 16 |
| Cache / Rate Limit | Redis 7 |
| Auth | JWT (JJWT 0.12) |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Build | Maven 3.9 |
| Containers | Docker & Docker Compose |
| Testing | JUnit 5, Mockito, Testcontainers |

## Running Locally

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Maven 3.9+

### Start infrastructure
```bash
make up          # starts PostgreSQL 16 + Redis 7
```

### Build & run
```bash
make build       # mvn clean package -DskipTests
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Run tests
```bash
make test        # mvn test (Testcontainers spins up its own DB)
```

### Useful endpoints (once running)
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

### Stop infrastructure
```bash
make down
```
