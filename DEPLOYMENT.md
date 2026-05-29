# Deployment Guide

This runbook explains how to deploy the URL Shortener service with Docker, Render, PostgreSQL, Redis, Flyway, and GitHub Actions.

The project includes deployment configuration, but this document does not claim a live production deployment unless you have verified one yourself.

## Deployment Model

```text
GitHub main
    |
    v
GitHub Actions
    |
    |-- mvn clean verify
    |
    v
Render deploy hook
    |
    v
Render Docker web service
    |
    |-- PostgreSQL managed database
    |-- Redis URL from external provider or managed Redis
    |-- Flyway migrations on startup
```

## Required Services

- Java 17 for local Maven commands.
- Docker Desktop for local PostgreSQL and Redis.
- PostgreSQL for persistent URL, user, and analytics data.
- Redis for redirect cache and rate limiting.
- Render account for the web service and managed PostgreSQL.
- Redis provider for production, such as Upstash or Render Redis.

Check provider pricing and free-tier limits before relying on a public demo. Free-tier services can sleep, throttle, expire, or require upgrades.

## Environment Variables

Use `.env.example` as the safe local template. Do not commit real secrets.

| Variable | Required | Used by | Notes |
|---|---:|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | Spring | Use `dev` locally and `prod` on Render. |
| `SERVER_PORT` | Local | Spring | Local server port, defaults to `8080`. |
| `PORT` | Render | Render/Spring prod | Render injects this automatically. |
| `APP_BASE_URL` | Yes | App | Public base URL used when returning short URLs. |
| `DB_HOST` | Yes | Spring datasource | Render wires this from managed PostgreSQL. |
| `DB_PORT` | Yes | Spring datasource | Render wires this from managed PostgreSQL. |
| `DB_NAME` | Yes | Spring datasource | Render wires this from managed PostgreSQL. |
| `DB_USER` | Yes | Spring datasource | Render wires this from managed PostgreSQL. |
| `DB_PASSWORD` | Yes | Spring datasource | Secret. Never commit real values. |
| `DATABASE_URL` | Optional | Tooling | Included for tools that expect a single JDBC URL. The app currently uses `DB_*`. |
| `REDIS_HOST` | Local | Spring Redis | Local Docker Compose Redis host. |
| `REDIS_PORT` | Local | Spring Redis | Local Docker Compose Redis port. |
| `REDIS_PASSWORD` | Optional | Spring Redis | Empty for local Redis. |
| `REDIS_URL` | Prod | Spring Redis prod | Full Redis URL, for example `rediss://...`. |
| `JWT_SECRET` | Yes | JWT signing | Use at least 32 random characters. Render can generate it. |
| `JWT_EXPIRATION` | Optional | JWT config | Expiration in hours. Defaults to `1`. |
| `CORS_ALLOWED_ORIGINS` | Optional | Security | Comma-separated allowed origins if needed. |
| `ANALYTICS_BATCH_SIZE` | Optional | Analytics consumer | Defaults to `500`. |
| `ANALYTICS_INTERVAL_MS` | Optional | Analytics consumer | Defaults to `1000`. |
| `ANALYTICS_QUEUE_CAPACITY` | Optional | Analytics publisher | Defaults to `10000`. |
| `ANALYTICS_SLOW_QUERY_THRESHOLD_MS` | Optional | Analytics service | Defaults to `500`. |

## Local Deployment Validation

Start infrastructure:

```bash
docker compose up -d
```

Compile and test:

```bash
mvn clean compile
mvn test
```

Run the application:

```bash
mvn spring-boot:run
```

Verify health:

```bash
curl http://localhost:8080/actuator/health
```

Run smoke tests:

```bash
bash bin/smoke-test.sh http://localhost:8080
```

## Docker Image Validation

Build the production image:

```bash
docker build -t url-shortener:local .
```

Run it against the local Docker Compose services:

```bash
docker run --rm \
  --network url-shortener_urlshortener-net \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PORT=8080 \
  -e DB_HOST=postgres \
  -e DB_PORT=5432 \
  -e DB_NAME=urlshortener \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e REDIS_URL=redis://redis:6379 \
  -e APP_BASE_URL=http://localhost:8080 \
  -e JWT_SECRET=replace-this-with-a-strong-32-character-minimum-secret \
  url-shortener:local
```

The `Dockerfile` uses Java 17, builds with Maven, runs on a JRE-only Alpine image, uses a non-root user, exposes port `8080`, and does not hardcode secrets.

## Render Deployment

The repository includes `render.yaml` for Render Blueprint deployment.

1. Push the repository to GitHub.
2. In Render, choose **New** -> **Blueprint**.
3. Connect the repository.
4. Review the resources from `render.yaml`.
5. Apply the blueprint.
6. Open the web service environment settings.
7. Set `REDIS_URL` to the production Redis connection string.
8. Set `APP_BASE_URL` to the final Render service URL, for example `https://your-service.onrender.com`.
9. Confirm `SPRING_PROFILES_ACTIVE=prod`.
10. Confirm `JWT_SECRET` was generated or set to a strong secret.
11. Trigger a deploy.

The Render service uses `/actuator/health` as the health check path. Flyway runs migrations during startup before the application accepts traffic.

## GitHub Actions CI/CD

The workflow in `.github/workflows/ci-cd.yml`:

- Runs on pushes and pull requests to `main`.
- Uses Java 17.
- Caches Maven dependencies.
- Runs `mvn clean verify`.
- Disables Testcontainers Ryuk in CI to avoid socket cleanup issues.
- Deploys to Render only on pushes to `main`.
- Validates required deployment secrets before triggering Render.
- Polls `/actuator/health`.
- Runs `bin/smoke-test.sh` after the service is healthy.

Add these GitHub Actions secrets before relying on automatic deploys:

| Secret | Value |
|---|---|
| `RENDER_DEPLOY_HOOK_URL` | Render web service deploy hook URL. |
| `APP_BASE_URL` | Public Render URL, for example `https://your-service.onrender.com`. |

## Production Checklist

- Use `SPRING_PROFILES_ACTIVE=prod`.
- Use a strong `JWT_SECRET`.
- Set `APP_BASE_URL` to the public HTTPS URL.
- Use TLS-capable Redis URLs when required by the provider.
- Confirm PostgreSQL credentials are injected by Render.
- Confirm Flyway migrations complete successfully.
- Confirm `/actuator/health` returns `UP`.
- Run smoke tests after deploy.
- Run k6 load tests only after the service is intentionally provisioned for that traffic.
- Record benchmark numbers only after measuring them.

## Database Migrations

Flyway is enabled in all profiles. In production:

- Migrations run automatically on startup.
- Hibernate uses `ddl-auto=validate`.
- Previously applied migration checksums are validated.
- Flyway clean is disabled.

If a migration fails, inspect the Render logs, fix the migration or schema mismatch, and redeploy. Do not edit already-applied migrations in a shared environment; create a new migration instead.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Build fails before tests | Java or Maven issue | Check GitHub Actions setup and Maven logs. |
| Testcontainers tests skip locally | Docker socket unavailable | Start Docker Desktop or run tests in a Docker-capable CI environment. |
| Render deploy job fails before curl | Missing GitHub secret | Add `RENDER_DEPLOY_HOOK_URL` and `APP_BASE_URL`. |
| `/actuator/health` is `DOWN` | Database or Redis unavailable | Check Render env vars and service logs. |
| Redis connection refused | Wrong `REDIS_URL` or provider blocked connection | Verify the URL, TLS scheme, and provider status. |
| Flyway validation fails | Schema and migrations differ | Compare latest migration with JPA entities. |
| Login/signup works locally but not in prod | Weak or missing `JWT_SECRET` | Set a strong production secret and redeploy. |
| First request is slow | Free-tier cold start | Wait for Render to wake the container, then retry. |

## Deployment Limitations

- No live deployment status is asserted in this repository.
- Free-tier Render services can cold start after inactivity.
- Provider limits can change; verify current Render and Redis limits before demos.
- This project does not include a separate production secrets manager.
- Docker Compose runs PostgreSQL and Redis locally, not the full app stack.
