# Deployment Guide

## Prerequisites

- Render account
- Upstash account for free-tier Redis
- GitHub repository connected to Render

## Step-By-Step Render Deployment

### 1. Create Upstash Redis

1. Go to Upstash and create a Redis database named `url-shortener-redis`.
2. Choose a region close to the Render service.
3. Copy the Redis connection URL, which starts with `rediss://`.

The production profile accepts `REDIS_URL`. Cache operations and rate limiting
fail open if Redis calls fail, so core URL operations remain available with
higher PostgreSQL load. Render health will still report a failed Redis component,
which makes Redis configuration strongly recommended.

### 2. Deploy The Render Blueprint

1. Go to Render, choose **New** -> **Blueprint**, and connect
   `mvkrishna24/url-shortener`.
2. Apply the infrastructure defined in `render.yaml`.
3. In the `url-shortener` web service environment, set:
   - `REDIS_URL` to the Upstash `rediss://` connection URL.
   - `APP_BASE_URL` to the final `https://<service>.onrender.com` URL.
4. Confirm Render generated `JWT_SECRET`.
5. Deploy the service.

The blueprint provisions PostgreSQL and injects its host, port, database,
username, and password. Flyway runs migrations during application startup.

### 3. Configure GitHub Actions Secrets

In GitHub, open **Settings** -> **Secrets and variables** -> **Actions** and add:

| Secret | Value |
|--------|-------|
| `RENDER_DEPLOY_HOOK_URL` | Render web service deploy hook URL |
| `RENDER_APP_URL` | Live application URL, for example `https://url-shortener-xxxx.onrender.com` |

Do not commit these values. After they are configured, every push to `main`
runs tests, verifies the Docker image, triggers Render, and polls the live health
endpoint.

### 4. Verify Deployment

```bash
curl https://your-app.onrender.com/actuator/health
curl -I https://your-app.onrender.com/swagger-ui.html
```

## Important Notes

- Free-tier services can sleep after inactivity, so the first request may be slow.
- Free PostgreSQL and Upstash limits can change; verify provider terms before demos.
- `/actuator/prometheus` requires a valid bearer token.
- Use a random JWT secret of at least 32 characters.

## Local Development

```bash
docker compose up -d
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | Yes | Set to `prod` in production |
| `DB_HOST` | Blueprint | PostgreSQL host injected by Render |
| `DB_PORT` | Blueprint | PostgreSQL port injected by Render |
| `DB_NAME` | Blueprint | PostgreSQL database injected by Render |
| `DB_USER` | Blueprint | PostgreSQL user injected by Render |
| `DB_PASSWORD` | Blueprint | PostgreSQL password injected by Render |
| `SPRING_DATASOURCE_URL` | Alternative | Full JDBC URL for non-blueprint deployments |
| `SPRING_DATASOURCE_USERNAME` | Alternative | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | Alternative | PostgreSQL password |
| `REDIS_URL` | Recommended | Upstash or managed Redis URL |
| `SPRING_REDIS_HOST` | Fallback | Redis host when `REDIS_URL` is absent |
| `SPRING_REDIS_PORT` | Fallback | Redis port when `REDIS_URL` is absent |
| `APP_BASE_URL` | Yes | Public application URL |
| `JWT_SECRET` | Yes | Random secret of at least 32 characters |
| `SERVER_PORT` | No | Defaults to `8080` |
