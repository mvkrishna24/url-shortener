# Operations Runbook

This runbook covers local development, common commands, health checks, logs, and
troubleshooting for the URL shortener.

## Local Prerequisites

- Java 17
- Maven 3.x
- Docker Desktop
- PostgreSQL and Redis from `docker-compose.yml`

## Start Local Dependencies

```bash
docker compose up -d postgres redis
```

Check containers:

```bash
docker compose ps
```

Expected services:

- `urlshortener-postgres`
- `urlshortener-redis`

## Run The Application

```bash
mvn spring-boot:run
```

The default profile is `dev` unless `SPRING_PROFILES_ACTIVE` is set.

Run with an explicit profile:

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

## Maven Commands

Compile:

```bash
mvn clean compile
```

Run tests:

```bash
mvn test
```

Package:

```bash
mvn clean package
```

Skip tests only when building a container image or doing a quick local package:

```bash
mvn clean package -DskipTests
```

## Health Checks

Application health:

```bash
curl http://localhost:8080/actuator/health
```

Actuator info:

```bash
curl http://localhost:8080/actuator/info
```

Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

PostgreSQL health:

```bash
docker compose exec postgres pg_isready -U postgres -d urlshortener
```

Redis health:

```bash
docker compose exec redis redis-cli ping
```

## Logs

Application logs appear in the terminal running `mvn spring-boot:run`.

Container logs:

```bash
docker compose logs -f postgres
docker compose logs -f redis
```

Useful patterns to search:

```bash
grep -i "flyway" app.log
grep -i "redis" app.log
grep -i "rate-limit" app.log
grep -i "analytics" app.log
```

## Database Access

Open `psql`:

```bash
docker compose exec postgres psql -U postgres -d urlshortener
```

Useful queries:

```sql
SELECT version();
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM urls;
SELECT COUNT(*) FROM clicks;
```

## Redis Access

Open Redis CLI:

```bash
docker compose exec redis redis-cli
```

Useful commands:

```text
PING
KEYS url:*
KEYS ratelimit:*
TTL url:someCode
```

## Reset Commands

Stop services:

```bash
docker compose down
```

Reset PostgreSQL and Redis volumes:

```bash
docker compose down -v
docker compose up -d postgres redis
```

Clear Redis only:

```bash
docker compose exec redis redis-cli FLUSHDB
```

## Testcontainers Skip Explanation

Integration tests extend a base class annotated with:

```text
@Testcontainers(disabledWithoutDocker = true)
```

If Docker is unavailable or the Docker socket cannot be reached, Testcontainers
tests are skipped instead of failing the entire test run.

This is expected in restricted local environments. In CI or local development
with Docker available, those tests should run against real PostgreSQL and Redis
containers.

## Load Testing

See [performance.md](performance.md).

Examples:

```bash
k6 run load-tests/warm-cache-redirect.js
k6 run load-tests/rate-limit.js
```

Do not record benchmark numbers unless the test was actually run and the machine,
commit SHA, and command are documented.

## Troubleshooting

### App fails to start: database connection refused

Check PostgreSQL:

```bash
docker compose ps postgres
docker compose logs postgres
```

Verify environment variables:

```bash
echo "$DB_HOST $DB_PORT $DB_NAME $DB_USER"
```

### App fails to start: Flyway validation error

Flyway validates migration checksums and schema state on startup.

Check:

```bash
docker compose exec postgres psql -U postgres -d urlshortener \
  -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
```

For local-only resets:

```bash
docker compose down -v
docker compose up -d postgres redis
```

### Redirects are slow locally

Check:

- Redis is running.
- The URL has been redirected at least once to warm the cache.
- Docker Desktop has enough CPU and memory.
- PostgreSQL is not saturated.

### Rate limiter does not return 429

Check:

- The request path starts with `/api/v1/`.
- Redis is running.
- The test uses one stable client identifier.
- The configured limits in `app.rate-limit.*` match expectations.

### Analytics country is null

This is expected if `GeoLite2-Country.mmdb` is not present. Click events still
persist; only country enrichment is disabled.

### Test run shows skipped integration tests

If the summary shows skipped Testcontainers tests, inspect Docker availability:

```bash
docker info
```

If Docker is available in your shell but tests still skip, check whether Maven is
running in an environment that can access the same Docker socket.
